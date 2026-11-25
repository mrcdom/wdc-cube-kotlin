/**
 * The matching rules of the conformance fixtures (`conformance/README.md`), over parsed JSON: partial objects,
 * `$absent`, `$any`, `$string`, `$number`, `$capture:<name>` and `${name}`, `scopes` by id, `releasedViews` as a set.
 */
export class FixtureMatcher {
  private readonly captured = new Map<string, unknown>()

  /** The template with every `${name}` replaced — in values, keys and inside strings. */
  substitute(template: unknown): unknown {
    if (Array.isArray(template)) {
      return template.map((it) => this.substitute(it))
    }
    if (isObject(template)) {
      const out: Record<string, unknown> = {}
      for (const [key, value] of Object.entries(template)) {
        out[this.substituteText(key)] = this.substitute(value)
      }
      return out
    }
    if (typeof template === 'string') {
      const whole = WHOLE.exec(template)
      if (whole) {
        return this.capturedValue(whole[1])
      }
      return this.substituteText(template)
    }
    return template
  }

  match(expected: unknown, actual: unknown, path = '$'): void {
    const present = actual !== undefined
    if (typeof expected === 'string') {
      if (expected === '$absent') {
        if (present) fail(`${path}: expected absent, was ${JSON.stringify(actual)}`)
      } else if (expected === '$any') {
        if (!present) fail(`${path}: expected a value, was absent`)
      } else if (expected === '$string') {
        if (typeof actual !== 'string') fail(`${path}: expected a string, was ${JSON.stringify(actual)}`)
      } else if (expected === '$number') {
        if (typeof actual !== 'number') fail(`${path}: expected a number, was ${JSON.stringify(actual)}`)
      } else if (expected.startsWith('$capture:')) {
        if (!present) fail(`${path}: expected a value to capture, was absent`)
        this.captured.set(expected.substring('$capture:'.length), actual)
      } else {
        this.matchValue(this.substitute(expected), actual, path)
      }
      return
    }
    if (!present) fail(`${path}: expected ${JSON.stringify(expected)}, was absent`)
    if (Array.isArray(expected)) {
      if (!Array.isArray(actual)) fail(`${path}: expected an array, was ${JSON.stringify(actual)}`)
      if (expected.length !== actual.length) {
        fail(`${path}: expected ${expected.length} elements, was ${actual.length}: ${JSON.stringify(actual)}`)
      }
      expected.forEach((item, i) => this.match(item, actual[i], `${path}[${i}]`))
    } else if (isObject(expected)) {
      if (!isObject(actual)) fail(`${path}: expected an object, was ${JSON.stringify(actual)}`)
      for (const [key, value] of Object.entries(expected)) {
        const name = this.substituteText(key)
        if (name === 'scopes' && Array.isArray(value)) {
          this.matchScopes(value, actual[name], `${path}.scopes`)
        } else if (name === 'releasedViews' && Array.isArray(value)) {
          this.matchSet(value, actual[name], `${path}.releasedViews`)
        } else {
          this.match(value, actual[name], `${path}.${name}`)
        }
      }
    } else {
      this.matchValue(expected, actual, path)
    }
  }

  private capturedValue(name: string): unknown {
    if (!this.captured.has(name)) fail(`\${${name}} was never captured`)
    return this.captured.get(name)
  }

  private substituteText(text: string): string {
    return text.replace(PART, (_, name: string) => {
      const value = this.capturedValue(name)
      return typeof value === 'string' ? value : JSON.stringify(value)
    })
  }

  private matchValue(expected: unknown, actual: unknown, path: string) {
    if (JSON.stringify(expected) !== JSON.stringify(actual)) {
      fail(`${path}: expected ${JSON.stringify(expected)}, was ${JSON.stringify(actual)}`)
    }
  }

  private matchScopes(expected: unknown[], actual: unknown, path: string) {
    if (!Array.isArray(actual)) fail(`${path}: expected scopes, was ${JSON.stringify(actual)}`)
    const byId = new Map<string, unknown>(actual.map((it) => [(it as { id: string }).id, it]))
    if (expected.length !== byId.size) fail(`${path}: expected ${expected.length} scopes, was ${[...byId.keys()]}`)
    for (const scope of expected) {
      const id = this.substitute((scope as { id: unknown }).id) as string
      const found = byId.get(id)
      if (found === undefined) fail(`${path}: no scope ${id} among ${[...byId.keys()]}`)
      this.match(scope, found, `${path}[${id}]`)
    }
  }

  private matchSet(expected: unknown[], actual: unknown, path: string) {
    if (!Array.isArray(actual)) fail(`${path}: expected an array, was ${JSON.stringify(actual)}`)
    const want = new Set(expected.map((it) => JSON.stringify(this.substitute(it))))
    const got = new Set(actual.map((it) => JSON.stringify(it)))
    if (want.size !== got.size || [...want].some((it) => !got.has(it))) {
      fail(`${path}: expected ${[...want]}, was ${[...got]}`)
    }
  }
}

const WHOLE = /^\$\{([A-Za-z0-9_-]+)\}$/
const PART = /\$\{([A-Za-z0-9_-]+)\}/g

function isObject(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}

function fail(message: string): never {
  throw new Error(message)
}
