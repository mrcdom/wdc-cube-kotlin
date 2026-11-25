export function format(value: number | undefined, decimalPoint = 2) {
  if (value == null) {
    return ''
  }

  if (isNaN(value)) {
    console.error('The given value is not numeric!')
    return ''
  }

  return value.toLocaleString('en-US', { minimumFractionDigits: decimalPoint })
}
