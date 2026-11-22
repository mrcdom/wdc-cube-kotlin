export function formatDate(dateOrMillis: number | Date | null | undefined) {
  if (dateOrMillis === undefined || dateOrMillis == null) {
    console.error('Date missing or invalid!')
    return 'Date unavailable'
  }

  const purchaseDate = typeof dateOrMillis === 'number' ? new Date(dateOrMillis) : dateOrMillis

  let purchaseDay = purchaseDate.getDay() + 1 //The first day returns zero
  let purchaseMonth = purchaseDate.getMonth() + 1 //January returns zero
  let purchaseFullYear = purchaseDate.getFullYear()

  let dayOutput = purchaseDay < 10 ? '0' + purchaseDay : purchaseDay
  let monthOutput = purchaseMonth < 10 ? '0' + purchaseMonth : purchaseMonth

  return monthOutput + '/' + dayOutput + '/' + purchaseFullYear
}
