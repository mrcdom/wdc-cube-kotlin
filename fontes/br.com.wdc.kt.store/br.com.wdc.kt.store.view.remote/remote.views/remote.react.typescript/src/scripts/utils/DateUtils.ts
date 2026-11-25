export function formatDate(dateOrMillis: number | Date | null | undefined) {
  if (dateOrMillis === undefined || dateOrMillis == null) {
    console.error('Date missing or invalid!')
    return 'Date unavailable'
  }

  const purchaseDate = typeof dateOrMillis === 'number' ? new Date(dateOrMillis) : dateOrMillis

  // The calendar date in UTC, as the other views show it. getDay() is the day of the week, not of the month.
  const purchaseDay = purchaseDate.getUTCDate()
  const purchaseMonth = purchaseDate.getUTCMonth() + 1 // January is zero
  const purchaseFullYear = purchaseDate.getUTCFullYear()

  const dayOutput = purchaseDay < 10 ? '0' + purchaseDay : purchaseDay
  const monthOutput = purchaseMonth < 10 ? '0' + purchaseMonth : purchaseMonth

  return monthOutput + '/' + dayOutput + '/' + purchaseFullYear
}
