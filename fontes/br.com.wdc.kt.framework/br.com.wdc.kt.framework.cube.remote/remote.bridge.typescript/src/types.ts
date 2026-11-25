/** The scope of the browser view (instance 0 of `BROWSER_VID`): the root of every screen. */
export type BrowserScope = {
  contentViewId?: string
  submitting?: boolean

  alertMessage?: {
    id: number
    args?: string[]
  }

  error?: {
    delay: number
    cause: unknown
    numAttempt: number
  }
}

/** The form fields queued for the next request, under each view's vsid, plus the request's own fields. */
export type FormMapType = {
  requestId?: number
  event?: string[]
} & Record<string, unknown>
