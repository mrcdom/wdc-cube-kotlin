import { languageOf, Messages } from '@root/messages'

/** The UI texts in the browser's language (pt-BR or, for any other, en-US), chosen once when the page loads. */
export const messages = new Messages(languageOf(navigator.language))

/** A text a presenter gives by its key in the catalog and the values of its placeholders (`errorMessage`). */
export type ScopeMessage = {
  key: string
  args?: Record<string, string>
}

/** A presenter's message in the browser's language; `undefined` when there is none. */
export function translate(message: ScopeMessage | null | undefined): string | undefined {
  return message ? messages.text(message.key, message.args ?? {}) : undefined
}
