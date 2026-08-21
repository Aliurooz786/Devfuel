import { useState, type FormEvent } from 'react'
import { localDateKey } from '../logs/timelineDisplay'
import { parseWeightInput, WEIGHT_MAX_KG, WEIGHT_MIN_KG } from './weightDisplay'

interface WeightFormProps {
  disabled?: boolean
  onSave: (value: number, occurrenceDate?: string) => void
}

export function WeightForm({ disabled, onSave }: WeightFormProps) {
  const [kg, setKg] = useState('')
  const [date, setDate] = useState('')
  const parsed = parseWeightInput(kg)
  const today = localDateKey(new Date().toISOString())
  const canSave = parsed != null && !disabled

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (parsed == null || disabled) {
      return
    }
    const occurrenceDate = date && date !== today ? date : undefined
    onSave(parsed, occurrenceDate)
  }

  return (
    <form className="weight-form" onSubmit={handleSubmit}>
      <div className="weight-form__fields">
        <div className="weight-form__field">
          <label className="weight-form__label" htmlFor="weight-kg">
            Weight in kilograms
          </label>
          <input
            id="weight-kg"
            className="weight-form__kg"
            type="number"
            inputMode="decimal"
            step="0.1"
            min={WEIGHT_MIN_KG}
            max={WEIGHT_MAX_KG}
            value={kg}
            onChange={(e) => setKg(e.target.value)}
            placeholder="93.5"
            disabled={disabled}
            autoComplete="off"
          />
        </div>
        <div className="weight-form__field">
          <label className="weight-form__label" htmlFor="weight-date">
            Date (optional)
          </label>
          <input
            id="weight-date"
            className="weight-form__date"
            type="date"
            max={today}
            value={date}
            onChange={(e) => setDate(e.target.value)}
            disabled={disabled}
          />
        </div>
      </div>
      <button className="weight-form__submit" type="submit" disabled={!canSave}>
        Save weight
      </button>
    </form>
  )
}
