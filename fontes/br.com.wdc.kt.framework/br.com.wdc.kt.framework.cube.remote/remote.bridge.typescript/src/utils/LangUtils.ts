export function deleteProperties(objectToClean: object) {
  const target = objectToClean as Record<string | symbol, unknown>
  for (const x in objectToClean) {
    if (Object.prototype.hasOwnProperty.call(target, x)) {
      delete target[x]
    }
  }
}

export function makeUniqueId() {
  return crypto.randomUUID() as string
}
