// Small charts built from plain HTML and the design tokens, so there is no chart library to learn.
// Every bar has its value written above it, so nothing depends on colour alone.

interface Series {
  name: string;
  values: number[];
  tone: "primary" | "secondary";
}

export function BarChart({ title, labels, series, format = (n) => String(n) }: { title: string; labels: string[]; series: Series[]; format?: (n: number) => string }) {
  const max = Math.max(1, ...series.flatMap((s) => s.values));
  return (
    <figure className="chart" style={{ margin: 0 }} aria-label={title}>
      <figcaption className="sq-text-headline">{title}</figcaption>
      {series.length > 1 && (
        <div className="chart-legend">
          {series.map((s) => (
            <span key={s.name}><i className={`swatch swatch-${s.tone}`} aria-hidden="true" />{s.name}</span>
          ))}
        </div>
      )}
      <div className="chart-scroll">
        <div className="bars" role="list">
          {labels.map((label, i) => (
            <div key={`${label}-${i}`} className="bar-group" role="listitem" aria-label={`${label}: ${series.map((s) => `${s.name} ${format(s.values[i] ?? 0)}`).join(", ")}`}>
              <div className="bar-pair">
                {series.map((s) => {
                  const v = s.values[i] ?? 0;
                  return (
                    <div key={s.name} className="bar-col">
                      <span className="bar-value">{format(v)}</span>
                      <div className={`bar bar-${s.tone}`} style={{ height: `${(v / max) * 82}%` }} />
                    </div>
                  );
                })}
              </div>
            </div>
          ))}
        </div>
        <div className="bar-labels" aria-hidden="true">
          {labels.map((label, i) => <span key={`${label}-${i}`} className="bar-label">{label}</span>)}
        </div>
      </div>
    </figure>
  );
}

export function HorizontalBars({ title, rows, empty }: { title: string; rows: { label: string; value: number; hint?: string }[]; empty: string }) {
  const max = Math.max(1, ...rows.map((r) => r.value));
  return (
    <figure className="chart" style={{ margin: 0 }} aria-label={title}>
      <figcaption className="sq-text-headline">{title}</figcaption>
      {rows.length === 0 ? (
        <p className="muted">{empty}</p>
      ) : (
        <div className="stack">
          {rows.map((r) => (
            <div key={r.label} className="hbar">
              <span title={r.hint}>{r.label}</span>
              <div className="hbar-track" aria-hidden="true"><div className="hbar-fill" style={{ width: `${(r.value / max) * 100}%` }} /></div>
              <span className="hbar-value">{r.value}</span>
            </div>
          ))}
        </div>
      )}
    </figure>
  );
}
