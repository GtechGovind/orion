//! JNI module for direct, in-process Orion kernel sessions.

use std::{
    collections::HashMap,
    panic::AssertUnwindSafe,
    ptr,
    sync::{
        Mutex, OnceLock,
        atomic::{AtomicI64, Ordering},
    },
};

use jni::{
    JNIEnv,
    objects::{JClass, JObject, JString, JValue},
    sys::{jlong, jobject},
};
use orion_ffi::session::RunSession;
use orion_protocol::{EffectResult, ProtocolError, StartRun};
use serde_json::{Map, Number, Value};

static RUNS: OnceLock<Mutex<HashMap<i64, RunSession>>> = OnceLock::new();
static NEXT_HANDLE: AtomicI64 = AtomicI64::new(1);

fn runs() -> &'static Mutex<HashMap<i64, RunSession>> {
    RUNS.get_or_init(|| Mutex::new(HashMap::new()))
}

fn with_run<T>(
    handle: jlong,
    operation: impl FnOnce(&mut RunSession) -> Result<T, String>,
) -> Result<T, String> {
    let mut runs = runs()
        .lock()
        .map_err(|_| "native run registry is poisoned".to_owned())?;
    let run = runs
        .get_mut(&handle)
        .ok_or_else(|| "native run handle is closed or invalid".to_owned())?;
    operation(run)
}

fn throw(env: &mut JNIEnv<'_>, error: impl std::fmt::Display) {
    let _ = env.throw_new("dev/orion/sdk/OrionException", error.to_string());
}

#[allow(
    clippy::needless_borrow,
    clippy::needless_borrows_for_generic_args,
    clippy::redundant_closure_for_method_calls,
    clippy::too_many_lines
)]
fn java_to_json(env: &mut JNIEnv<'_>, value: &JObject<'_>) -> Result<Value, String> {
    if value.is_null() {
        return Ok(Value::Null);
    }
    if env
        .is_instance_of(&value, "java/lang/String")
        .map_err(to_string)?
    {
        let string: &JString<'_> = value.into();
        return env
            .get_string(&string)
            .map(|value| Value::String(value.into()))
            .map_err(to_string);
    }
    if env
        .is_instance_of(&value, "java/lang/Boolean")
        .map_err(to_string)?
    {
        let boolean = env
            .call_method(&value, "booleanValue", "()Z", &[])
            .and_then(|value| value.z())
            .map_err(to_string)?;
        return Ok(Value::Bool(boolean));
    }
    if env
        .is_instance_of(&value, "java/lang/Number")
        .map_err(to_string)?
    {
        if env
            .is_instance_of(&value, "java/lang/Byte")
            .map_err(to_string)?
            || env
                .is_instance_of(&value, "java/lang/Short")
                .map_err(to_string)?
            || env
                .is_instance_of(&value, "java/lang/Integer")
                .map_err(to_string)?
            || env
                .is_instance_of(&value, "java/lang/Long")
                .map_err(to_string)?
        {
            let integer = env
                .call_method(&value, "longValue", "()J", &[])
                .and_then(|value| value.j())
                .map_err(to_string)?;
            return Ok(Value::Number(Number::from(integer)));
        }
        let number = env
            .call_method(&value, "doubleValue", "()D", &[])
            .and_then(|value| value.d())
            .map_err(to_string)?;
        return Number::from_f64(number)
            .map(Value::Number)
            .ok_or_else(|| "floating-point values must be finite".to_owned());
    }
    if env
        .is_instance_of(&value, "java/util/Map")
        .map_err(to_string)?
    {
        let entries = env
            .call_method(&value, "entrySet", "()Ljava/util/Set;", &[])
            .and_then(|value| value.l())
            .map_err(to_string)?;
        let iterator = env
            .call_method(&entries, "iterator", "()Ljava/util/Iterator;", &[])
            .and_then(|value| value.l())
            .map_err(to_string)?;
        let mut result = Map::new();
        loop {
            let has_next = env
                .call_method(&iterator, "hasNext", "()Z", &[])
                .and_then(|value| value.z())
                .map_err(to_string)?;
            if !has_next {
                break;
            }
            let entry = env
                .call_method(&iterator, "next", "()Ljava/lang/Object;", &[])
                .and_then(|value| value.l())
                .map_err(to_string)?;
            let key = env
                .call_method(&entry, "getKey", "()Ljava/lang/Object;", &[])
                .and_then(|value| value.l())
                .map_err(to_string)?;
            let key: String = env
                .get_string(&JString::from(key))
                .map_err(to_string)?
                .into();
            let item = env
                .call_method(&entry, "getValue", "()Ljava/lang/Object;", &[])
                .and_then(|value| value.l())
                .map_err(to_string)?;
            result.insert(key, java_to_json(env, &item)?);
            env.delete_local_ref(entry).map_err(to_string)?;
        }
        return Ok(Value::Object(result));
    }
    if env
        .is_instance_of(&value, "java/util/List")
        .map_err(to_string)?
    {
        let size = env
            .call_method(&value, "size", "()I", &[])
            .and_then(|value| value.i())
            .map_err(to_string)?;
        let capacity = usize::try_from(size).map_err(|_| "JVM list size is negative".to_owned())?;
        let mut result = Vec::with_capacity(capacity);
        for index in 0..size {
            let item = env
                .call_method(
                    &value,
                    "get",
                    "(I)Ljava/lang/Object;",
                    &[JValue::Int(index)],
                )
                .and_then(|value| value.l())
                .map_err(to_string)?;
            result.push(java_to_json(env, &item)?);
            env.delete_local_ref(item).map_err(to_string)?;
        }
        return Ok(Value::Array(result));
    }
    Err("native DTO contains an unsupported JVM value".to_owned())
}

fn json_to_java<'local>(
    env: &mut JNIEnv<'local>,
    value: &Value,
) -> Result<JObject<'local>, String> {
    match value {
        Value::Null => Ok(JObject::null()),
        Value::Bool(value) => env
            .new_object(
                "java/lang/Boolean",
                "(Z)V",
                &[JValue::Bool(u8::from(*value))],
            )
            .map_err(to_string),
        Value::Number(value) if value.is_i64() || value.is_u64() => {
            let value = value
                .as_i64()
                .or_else(|| value.as_u64().and_then(|value| i64::try_from(value).ok()))
                .ok_or_else(|| "integer exceeds the JVM signed 64-bit range".to_owned())?;
            env.new_object("java/lang/Long", "(J)V", &[JValue::Long(value)])
                .map_err(to_string)
        }
        Value::Number(value) => env
            .new_object(
                "java/lang/Double",
                "(D)V",
                &[JValue::Double(
                    value.as_f64().ok_or_else(|| "invalid number".to_owned())?,
                )],
            )
            .map_err(to_string),
        Value::String(value) => env.new_string(value).map(JObject::from).map_err(to_string),
        Value::Array(values) => {
            let list = env
                .new_object("java/util/ArrayList", "()V", &[])
                .map_err(to_string)?;
            for value in values {
                let item = json_to_java(env, value)?;
                env.call_method(
                    &list,
                    "add",
                    "(Ljava/lang/Object;)Z",
                    &[JValue::Object(&item)],
                )
                .map_err(to_string)?;
                env.delete_local_ref(item).map_err(to_string)?;
            }
            Ok(list)
        }
        Value::Object(values) => {
            let map = env
                .new_object("java/util/HashMap", "()V", &[])
                .map_err(to_string)?;
            for (key, value) in values {
                let key = env.new_string(key).map(JObject::from).map_err(to_string)?;
                let value = json_to_java(env, value)?;
                env.call_method(
                    &map,
                    "put",
                    "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                    &[JValue::Object(&key), JValue::Object(&value)],
                )
                .map_err(to_string)?;
                env.delete_local_ref(key).map_err(to_string)?;
                env.delete_local_ref(value).map_err(to_string)?;
            }
            Ok(map)
        }
    }
}

fn to_string(error: impl std::fmt::Display) -> String {
    error.to_string()
}

fn run_step(
    env: &mut JNIEnv<'_>,
    operation: impl FnOnce() -> Result<orion_kernel::Step, String>,
) -> jobject {
    match std::panic::catch_unwind(AssertUnwindSafe(operation)) {
        Ok(Ok(step)) => match serde_json::to_value(step)
            .map_err(to_string)
            .and_then(|value| json_to_java(env, &value))
        {
            Ok(value) => value.into_raw(),
            Err(error) => {
                throw(env, error);
                ptr::null_mut()
            }
        },
        Ok(Err(error)) => {
            throw(env, error);
            ptr::null_mut()
        }
        Err(_) => {
            throw(env, "native Orion kernel panicked");
            ptr::null_mut()
        }
    }
}

#[unsafe(no_mangle)]
/// Creates a Rust-owned run and returns its opaque JVM handle.
pub extern "system" fn Java_dev_orion_sdk_NativeKernel_create(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    command: JObject<'_>,
) -> jlong {
    let result = std::panic::catch_unwind(AssertUnwindSafe(|| {
        let value = java_to_json(&mut env, &command)?;
        let command: StartRun = serde_json::from_value(value).map_err(to_string)?;
        let session = RunSession::start(command).map_err(to_string)?;
        let handle = NEXT_HANDLE.fetch_add(1, Ordering::Relaxed);
        runs()
            .lock()
            .map_err(|_| "native run registry is poisoned".to_owned())?
            .insert(handle, session);
        Ok::<_, String>(handle)
    }));
    match result {
        Ok(Ok(handle)) => handle,
        Ok(Err(error)) => {
            throw(&mut env, error);
            0
        }
        Err(_) => {
            throw(&mut env, "native Orion kernel panicked");
            0
        }
    }
}

#[unsafe(no_mangle)]
/// Returns the initial unread step for a native run.
pub extern "system" fn Java_dev_orion_sdk_NativeKernel_takeStep(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    handle: jlong,
) -> jobject {
    run_step(&mut env, || {
        with_run(handle, |run| {
            run.take_step()
                .ok_or_else(|| "no unread kernel step".to_owned())
        })
    })
}

#[unsafe(no_mangle)]
/// Resumes a native run from a JVM effect-result DTO.
pub extern "system" fn Java_dev_orion_sdk_NativeKernel_resume(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    handle: jlong,
    result: JObject<'_>,
) -> jobject {
    let value = match java_to_json(&mut env, &result)
        .and_then(|value| serde_json::from_value::<EffectResult>(value).map_err(to_string))
    {
        Ok(value) => value,
        Err(error) => {
            throw(&mut env, error);
            return ptr::null_mut();
        }
    };
    run_step(&mut env, || {
        with_run(handle, |run| run.resume(value).map_err(to_string))
    })
}

#[unsafe(no_mangle)]
/// Cancels a native run.
pub extern "system" fn Java_dev_orion_sdk_NativeKernel_cancel(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    handle: jlong,
) -> jobject {
    run_step(&mut env, || with_run(handle, |run| Ok(run.cancel())))
}

#[unsafe(no_mangle)]
/// Fails a native run from a JVM protocol-error DTO.
pub extern "system" fn Java_dev_orion_sdk_NativeKernel_fail(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    handle: jlong,
    error: JObject<'_>,
) -> jobject {
    let value = match java_to_json(&mut env, &error)
        .and_then(|value| serde_json::from_value::<ProtocolError>(value).map_err(to_string))
    {
        Ok(value) => value,
        Err(error) => {
            throw(&mut env, error);
            return ptr::null_mut();
        }
    };
    run_step(&mut env, || {
        with_run(handle, |run| run.fail(value).map_err(to_string))
    })
}

#[unsafe(no_mangle)]
/// Releases a Rust-owned run handle.
pub extern "system" fn Java_dev_orion_sdk_NativeKernel_close(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    handle: jlong,
) {
    if let Err(error) = runs()
        .lock()
        .map_err(|_| "native run registry is poisoned".to_owned())
        .map(|mut runs| runs.remove(&handle))
    {
        throw(&mut env, error);
    }
}
