interface BackdatedNoticeProps {
  placement: string
  onShow: () => void
  onDismiss: () => void
}

export function BackdatedNotice({ placement, onShow, onDismiss }: BackdatedNoticeProps) {
  return (
    <p className="backdated-notice" role="status">
      <span className="backdated-notice__text">
        Saved to <strong>{placement}</strong>, not now.
      </span>
      <button type="button" className="backdated-notice__action" onClick={onShow}>
        Show
      </button>
      <button
        type="button"
        className="backdated-notice__dismiss"
        onClick={onDismiss}
        aria-label="Dismiss"
      >
        ×
      </button>
    </p>
  )
}
