const MONTHS = [
  'January','February','March','April','May','June',
  'July','August','September','October','November','December',
]

function daysInMonth(month, year) {
  if (!month || !year) return 31
  return new Date(Number(year), Number(month), 0).getDate()
}

const selectClass =
  'px-2 py-1.5 border border-forest-15 rounded-button text-sm bg-parchment text-forest focus:outline-none focus:ring-2 focus:ring-forest-25'

export function DatePickerDropdowns({ day, month, year, onChange, minYear }) {
  const currentYear = new Date().getFullYear()
  const startYear = minYear ?? currentYear
  const years = Array.from({ length: 100 }, (_, i) => startYear + i)
  const maxDay = daysInMonth(month, year)
  const days = Array.from({ length: maxDay }, (_, i) => i + 1)

  function set(field, value) {
    const next = { day, month, year, [field]: value ? Number(value) : '' }
    if (field === 'month' || field === 'year') {
      const max = daysInMonth(next.month, next.year)
      if (next.day > max) next.day = max
    }
    onChange(next)
  }

  return (
    <div className="flex gap-2 flex-wrap">
      <select
        value={day ?? ''}
        onChange={(e) => set('day', e.target.value)}
        className={selectClass}
        aria-label="Day"
      >
        <option value="">Day</option>
        {days.map((d) => (
          <option key={d} value={d}>{d}</option>
        ))}
      </select>

      <select
        value={month ?? ''}
        onChange={(e) => set('month', e.target.value)}
        className={selectClass}
        aria-label="Month"
      >
        <option value="">Month</option>
        {MONTHS.map((m, i) => (
          <option key={m} value={i + 1}>{m}</option>
        ))}
      </select>

      <select
        value={year ?? ''}
        onChange={(e) => set('year', e.target.value)}
        className={selectClass}
        aria-label="Year"
      >
        <option value="">Year</option>
        {years.map((y) => (
          <option key={y} value={y}>{y}</option>
        ))}
      </select>
    </div>
  )
}
