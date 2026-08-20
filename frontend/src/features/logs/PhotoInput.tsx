import type { ChangeEvent } from 'react'

interface PhotoInputProps {
  disabled?: boolean
  note: string
  onNoteChange: (value: string) => void
  onSelect: (file: File) => void
}

export function PhotoInput({ disabled, note, onNoteChange, onSelect }: PhotoInputProps) {
  function handleChange(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (file) {
      onSelect(file)
    }
  }

  return (
    <div className="photo-block">
      <label className="photo-block__note-label" htmlFor="photo-note">
        Optional Note
      </label>
      <input
        id="photo-note"
        className="photo-block__note"
        type="text"
        value={note}
        onChange={(e) => onNoteChange(e.target.value)}
        placeholder="Office lunch, extra sweets today…"
        disabled={disabled}
        autoComplete="off"
        maxLength={2000}
      />
      <label className="photo-input">
        <input
          className="photo-input__file"
          type="file"
          accept="image/jpeg,image/png,image/webp"
          capture="environment"
          disabled={disabled}
          onChange={handleChange}
        />
        Photo
      </label>
    </div>
  )
}
