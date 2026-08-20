import type { FormEvent } from 'react'

interface LogInputProps {
  value: string
  onChange: (value: string) => void
  onSubmit: () => void
  disabled?: boolean
}

export function LogInput({ value, onChange, onSubmit, disabled }: LogInputProps) {
  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (!value.trim() || disabled) {
      return
    }
    onSubmit()
  }

  return (
    <form className="log-input" onSubmit={handleSubmit}>
      <label className="log-input__label" htmlFor="log-message">
        Log an event
      </label>
      <div className="log-input__row">
        <input
          id="log-message"
          className="log-input__field"
          type="text"
          value={value}
          onChange={(e) => onChange(e.target.value)}
          placeholder="e.g. 1 cigarette pee li"
          disabled={disabled}
          autoComplete="off"
          maxLength={2000}
        />
        <button className="log-input__submit" type="submit" disabled={disabled || !value.trim()}>
          Log
        </button>
      </div>
    </form>
  )
}
