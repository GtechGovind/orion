/**
 * Returns arguments that invoke the same npm CLI which launched the current
 * package script. Calling the JavaScript entry point avoids Windows `.cmd`
 * process-launch differences without enabling a command shell.
 */
export function npmCliArguments(arguments_) {

  const npmCliPath = process.env.npm_execpath;

  if (!npmCliPath) {
    throw new Error("this package command must be launched through npm");
  }

  return [npmCliPath, ...arguments_];

}
