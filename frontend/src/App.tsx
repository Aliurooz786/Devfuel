import { useCallback, useEffect, useState } from 'react'
import './App.css'
import { createImageLog, createLog, getTimeline } from './api/logsApi'
import { createWeight, getLatestWeight, getWeightHistory } from './api/weightApi'
import { BackdatedNotice } from './features/logs/BackdatedNotice'
import { LogInput } from './features/logs/LogInput'
import { PhotoInput } from './features/logs/PhotoInput'
import { Timeline } from './features/logs/Timeline'
import { describeOccurrence } from './features/logs/timelineDisplay'
import { WeightForm } from './features/weight/WeightForm'
import { WeightHistory } from './features/weight/WeightHistory'
import { WeightWidget } from './features/weight/WeightWidget'
import type { CreateLogResponse, LogItemResponse } from './types/log'
import type { WeightPoint } from './types/weight'

interface BackdatedPlacement {
  id: string
  placement: string
}

function App() {
  const [message, setMessage] = useState('')
  const [logs, setLogs] = useState<LogItemResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [photoNote, setPhotoNote] = useState('')
  const [backdated, setBackdated] = useState<BackdatedPlacement | null>(null)
  const [scrollKey, setScrollKey] = useState<number | undefined>(undefined)
  const [latestWeight, setLatestWeight] = useState<WeightPoint | null>(null)
  const [weightPoints, setWeightPoints] = useState<WeightPoint[]>([])
  const [weightLoading, setWeightLoading] = useState(true)
  const [weightError, setWeightError] = useState<string | null>(null)
  const [weightFormKey, setWeightFormKey] = useState(0)

  const refreshTimeline = useCallback(async () => {
    const timeline = await getTimeline()
    setLogs(timeline)
  }, [])

  const refreshWeight = useCallback(async () => {
    const [latestResult, historyResult] = await Promise.allSettled([
      getLatestWeight(),
      getWeightHistory(),
    ])
    if (latestResult.status === 'fulfilled') {
      setLatestWeight(latestResult.value)
    } else {
      setWeightError('Could not load weight.')
    }
    if (historyResult.status === 'fulfilled') {
      setWeightPoints(historyResult.value.points)
    } else {
      setWeightError('Could not load weight.')
    }
    if (latestResult.status === 'fulfilled' && historyResult.status === 'fulfilled') {
      setWeightError(null)
    }
  }, [])

  const refreshAll = useCallback(async () => {
    await Promise.all([refreshTimeline(), refreshWeight()])
  }, [refreshTimeline, refreshWeight])

  const noteBackdated = useCallback((created: CreateLogResponse) => {
    if (!created.loggedLater) {
      setBackdated(null)
      return
    }
    setBackdated({
      id: created.id,
      placement: describeOccurrence(created.timestamp, created.eventTimePrecision),
    })
  }, [])

  useEffect(() => {
    let cancelled = false

    async function load() {
      setLoading(true)
      setWeightLoading(true)
      setError(null)
      try {
        await refreshAll()
      } catch {
        if (!cancelled) {
          setError('Could not load timeline.')
        }
      } finally {
        if (!cancelled) {
          setLoading(false)
          setWeightLoading(false)
        }
      }
    }

    void load()
    return () => {
      cancelled = true
    }
  }, [refreshAll])

  async function afterCreate(created: CreateLogResponse) {
    noteBackdated(created)
    if (created.eventType === 'WEIGHT') {
      await refreshAll()
    } else {
      await refreshTimeline()
    }
  }

  async function handleImage(file: File) {
    setSubmitting(true)
    setError(null)
    try {
      const created = await createImageLog(file, 'web', photoNote)
      setPhotoNote('')
      await afterCreate(created)
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
      const created = await createLog({ message: message.trim(), source: 'web' })
      setMessage('')
      await afterCreate(created)
    } catch {
      setError('Could not save log. Try again.')
    } finally {
      setSubmitting(false)
    }
  }

  async function handleWeightSave(value: number, occurrenceDate?: string) {
    setSubmitting(true)
    setError(null)
    try {
      const created = await createWeight({
        value,
        unit: 'kg',
        occurrenceDate,
        source: 'web',
      })
      setWeightFormKey((key) => key + 1)
      await afterCreate(created)
    } catch (cause) {
      const status = cause instanceof Error ? cause.message : ''
      if (status.includes('API 400')) {
        setError('Weight must be between 30 and 250 kg.')
      } else {
        setError('Could not save weight. Try again.')
      }
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
        {backdated ? (
          <BackdatedNotice
            placement={backdated.placement}
            onShow={() => setScrollKey((key) => (key ?? 0) + 1)}
            onDismiss={() => setBackdated(null)}
          />
        ) : null}
        {error ? <p className="app__error">{error}</p> : null}
      </section>

      <section className="app__weight" aria-label="Weight" aria-busy={weightLoading || submitting}>
        <h2 className="app__section-title">Weight</h2>
        <WeightWidget latest={latestWeight} loading={weightLoading} />
        {weightError ? <p className="app__error">{weightError}</p> : null}
        <WeightForm key={weightFormKey} disabled={submitting} onSave={handleWeightSave} />
        <WeightHistory points={weightPoints} />
      </section>

      <section className="app__timeline" aria-label="Timeline">
        <h2 className="app__section-title">Timeline</h2>
        <Timeline
          logs={logs}
          loading={loading}
          highlightId={backdated?.id ?? null}
          scrollKey={scrollKey}
        />
      </section>
    </main>
  )
}

export default App
