"""Objective checks for Ilmerya audio: level, clipping, edge clicks, harsh-band energy, loop seam.

Usage: python tools/analyze_audio.py <dir with ilmerya-original-score.wav and effects/>
Prints one line per file. These measurements support, but do not replace, listening on devices.
"""
import sys, wave
from pathlib import Path
import numpy as np


def load(path):
    with wave.open(str(path)) as w:
        channels, rate = w.getnchannels(), w.getframerate()
        data = np.frombuffer(w.readframes(w.getnframes()), dtype="<i2").astype(np.float64) / 32768
    return data.reshape(-1, channels), rate


def db(x):
    return 20 * np.log10(max(x, 1e-9))


def band_share(mono, rate, lo, hi):
    spectrum = np.abs(np.fft.rfft(mono * np.hanning(len(mono)))) ** 2
    freqs = np.fft.rfftfreq(len(mono), 1 / rate)
    total = spectrum[(freqs > 40)].sum()
    return spectrum[(freqs >= lo) & (freqs < hi)].sum() / total if total else 0


def centroid(mono, rate):
    spectrum = np.abs(np.fft.rfft(mono * np.hanning(len(mono))))
    freqs = np.fft.rfftfreq(len(mono), 1 / rate)
    return (spectrum * freqs).sum() / spectrum.sum()


def report(path, loop=False):
    data, rate = load(path)
    mono = data.mean(axis=1)
    peak = np.abs(data).max()
    rms = np.sqrt((mono ** 2).mean())
    # Short-term loudness proxy: loudest 50 ms window RMS.
    win = int(rate * .05)
    st = max(np.sqrt(np.convolve(mono ** 2, np.ones(win) / win, mode="valid")).max(), 1e-9)
    clipped = int((np.abs(data) > .999).sum())
    first = np.abs(data[:8]).max()
    tail = np.abs(data[-int(rate * .005):]).max()
    # Largest sample-to-sample jump relative to the local level reveals clicks.
    jump = np.abs(np.diff(mono)).max()
    harsh = band_share(mono, rate, 2000, 5000)
    line = (f"{path.name:28s} {len(mono) / rate:5.2f}s peak {db(peak):6.1f} dBFS  rms {db(rms):6.1f}  "
            f"loud50ms {db(st):6.1f}  centroid {centroid(mono, rate):6.0f} Hz  2-5k {harsh * 100:4.1f}%  "
            f"start {first:.4f} tail {tail:.4f} maxstep {jump:.3f} clip {clipped}")
    if loop:
        seam = np.abs(data[0] - data[-1]).max()
        typical = np.abs(np.diff(data, axis=0)).mean()
        line += f"  seam {seam:.4f} (typical step {typical:.4f})"
    print(line)
    return db(st)


def main():
    root = Path(sys.argv[1] if len(sys.argv) > 1 else "shared/build/reports/audio")
    music = report(root / "ilmerya-original-score.wav", loop=True)
    levels = {p.name: report(p) for p in sorted((root / "effects").glob("*.wav"))}
    print(f"\nMusic loud50ms {music:.1f} dBFS (at app volume 0.45: {music + db(.45):.1f}); "
          f"effects range {min(levels.values()):.1f}..{max(levels.values()):.1f} dBFS (at 0.70: +{db(.7):.1f} dB)")


if __name__ == "__main__":
    main()
