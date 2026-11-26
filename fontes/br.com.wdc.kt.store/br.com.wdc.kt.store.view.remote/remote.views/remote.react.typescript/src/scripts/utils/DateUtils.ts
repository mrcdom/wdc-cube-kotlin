import { messages } from '@root/i18n'

export function formatDate(dateOrMillis: number | Date | null | undefined) {
  if (dateOrMillis === undefined || dateOrMillis == null) {
    console.error('Date missing or invalid!')
    return messages.text('common.dateUnavailable')
  }

  const purchaseDate = typeof dateOrMillis === 'number' ? new Date(dateOrMillis) : dateOrMillis

  // The calendar date in UTC, as the other views show it. getDay() is the day of the week, not of the month.
  return messages.date(purchaseDate.getUTCFullYear(), purchaseDate.getUTCMonth() + 1, purchaseDate.getUTCDate())
}
