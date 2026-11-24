export function formatDate(dateOrMillis: number | Date | null | undefined) {
  if (dateOrMillis === undefined || dateOrMillis == null) {
    console.error('Date missing or invalid!')
    return 'Date unavailable'
  }

  const purchaseDate = typeof dateOrMillis === 'number' ? new Date(dateOrMillis) : dateOrMillis

  // The calendar date in UTC, as the other views show it. getDay() is the day of the week, not of the month.
  let purchaseDay = purchaseDate.getUTCDate()
  let purchaseMonth = purchaseDate.getUTCMonth() + 1 // January is zero
  let purchaseFullYear = purchaseDate.getUTCFullYear()

  let dayOutput = purchaseDay < 10 ? '0' + purchaseDay : purchaseDay
  let monthOutput = purchaseMonth < 10 ? '0' + purchaseMonth : purchaseMonth

  return monthOutput + '/' + dayOutput + '/' + purchaseFullYear
}
