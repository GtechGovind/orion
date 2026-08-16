//! Run, session, turn, action, checkpoint, and operation identities.

use serde::{Deserialize, Serialize};

macro_rules! identifier {
    ($name:ident) => {
        #[doc = "A stable protocol identifier."]
        #[derive(Clone, Debug, Eq, Hash, Ord, PartialEq, PartialOrd, Serialize, Deserialize)]
        #[serde(transparent)]
        pub struct $name(pub String);

        impl From<&str> for $name {
            fn from(value: &str) -> Self {
                Self(value.to_owned())
            }
        }
    };
}

identifier!(RunId);
identifier!(TurnId);
identifier!(ActionId);
