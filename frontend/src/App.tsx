import { useCallback, useEffect, useState } from 'react'
import './App.css'
import { createImageLog, createLog, getTimeline } from './api/logsApi'
import { LogInput } from './features/logs/LogInput'
import { PhotoInput } from './features/logs/PhotoInput'
import { Timeline } from './features/logs/Timeline'
import type { LogItemResponse } from './types/log'

function App() {
  const [message, setMessage] = useState('')
  const [logs, setLogs] = useState<LogItemResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [photoNote, setPhotoNote] = useState('')

  const refreshTimeline = useCallback(async () => {
    const timeline = await getTimeline()
    setLogs(timeline)
  }, [])

  useEffect(() => {
    let cancelled = false

    async function load() {
      setLoading(true)
      setError(null)
      try {
        const timeline = await getTimeline()
        if (!cancelled) {
          setLogs(timeline)
        }
      } catch {
        if (!cancelled) {
          setError('Could not load timeline.')
        }
      } finally {
        if (!cancelled) {
          setLoading(false)
        }
      }
    }

    void load()
    return () => {
      cancelled = true
    }
  }, [])

  async function handleImage(file: File) {
    setSubmitting(true)
    setError(null)
    try {
      await createImageLog(file, 'web', photoNote)
      setPhotoNote('')
      await refreshTimeline()
    } catch {
      setError('Could not save photo log. Try again.')
    } finally {
      setSubmitting(false)
    }
  }

  async function handleSubmit() {
    setSubmitting(true)
    setError(null)
    try {
      await createLog({ message: message.trim(), source: 'web' })
      setMessage('')
      await refreshTimeline()
    } catch {
      setError('Could not save log. Try again.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="app">
      <header className="app__header">
        <h1 className="app__brand">DevFuel</h1>
        <p className="app__tagline">Log what happened. Keep it honest.</p>
      </header>

      <section className="app__compose" aria-label="Create log">
        <LogInput
          value={message}
          onChange={setMessage}
          onSubmit={() => {
            void handleSubmit()
          }}
          disabled={submitting}
        />
        <PhotoInput
          disabled={submitting}
          note={photoNote}
          onNoteChange={setPhotoNote}
          onSelect={(file) => {
            void handleImage(file)
          }}
        />
        {error ? <p className="app__error">{error}</p> : null}
      </section>

      <section className="app__timeline" aria-label="Timeline">
        <h2 className="app__section-title">Timeline</h2>
        <Timeline logs={logs} loading={loading} />
      </section>
    </main>
  )
}

export default App
