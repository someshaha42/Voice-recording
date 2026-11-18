import React, { useMemo } from 'react';
import { EqualizerEffect } from '../dsp/EffectsRackDsp';
import { EqualizerConfig } from '../types/audio';

interface EqualizerResponseGraphProps {
  config: EqualizerConfig;
}

const FREQ_POINTS = (() => {
  const arr = new Float32Array(64);
  for (let i = 0; i < 64; i++) {
    const t = i / 63;
    arr[i] = 20.0 * Math.pow(1000.0, t);
  }
  return arr;
})();

const W = 500;
const H = 92;
const MID_Y = H * 0.5;

function logX(freqHz: number): number {
  const clamped = Math.max(20, Math.min(20000, freqHz));
  const norm = Math.log(clamped / 20) / Math.log(1000);
  return Math.max(0, Math.min(1, norm)) * W;
}

function gainY(db: number): number {
  return Math.max(2, Math.min(H - 2, MID_Y - (db / 15) * (H * 0.45)));
}

export const EqualizerResponseGraph: React.FC<EqualizerResponseGraphProps> = React.memo(({ config }) => {
  const { linePath, fillPath, nodes } = useMemo(() => {
    const responseDb = EqualizerEffect.computeMagnitudeCurveDb(config, FREQ_POINTS);
    let line = '';
    let fill = '';
    for (let i = 0; i < responseDb.length; i++) {
      const x = (i / (responseDb.length - 1)) * W;
      const y = gainY(responseDb[i]);
      if (i === 0) {
        line = `M ${x.toFixed(2)} ${y.toFixed(2)}`;
        fill = `M ${x.toFixed(2)} ${MID_Y.toFixed(2)} L ${x.toFixed(2)} ${y.toFixed(2)}`;
      } else {
        line += ` L ${x.toFixed(2)} ${y.toFixed(2)}`;
        fill += ` L ${x.toFixed(2)} ${y.toFixed(2)}`;
      }
    }
    fill += ` L ${W} ${MID_Y.toFixed(2)} Z`;

    const bandNodes = [
      { x: logX(config.lowFreqHz), y: gainY(config.lowGainDb) },
      { x: logX(config.mid1FreqHz), y: gainY(config.mid1GainDb) },
      { x: logX(config.mid2FreqHz), y: gainY(config.mid2GainDb) },
      { x: logX(config.mid3FreqHz), y: gainY(config.mid3GainDb) },
      { x: logX(config.highFreqHz), y: gainY(config.highGainDb) },
    ];

    return { linePath: line, fillPath: fill, nodes: bandNodes };
  }, [config]);

  return (
    <div className="w-full rounded-[10px] bg-[#131822] border border-[#2C374D] p-2.5">
      <div className="flex items-center justify-between text-[11px] mb-1.5">
        <span className="text-[#64748B] font-semibold tracking-wider">EQ FREQUENCY RESPONSE</span>
        <span className="text-[#00D4FF] font-mono-tabular">±15 dB</span>
      </div>

      <svg
        viewBox={`0 0 ${W} ${H}`}
        className="w-full h-[92px] block"
        preserveAspectRatio="none"
        aria-label="Equalizer frequency response curve"
      >
        <defs>
          <linearGradient id="eqFillGrad" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="#00D4FF" stopOpacity="0.28" />
            <stop offset="100%" stopColor="#00D4FF" stopOpacity="0" />
          </linearGradient>
        </defs>

        {[-6, 0, 6].map((db) => {
          const y = MID_Y - (db / 15) * (H * 0.45);
          return (
            <line
              key={db}
              x1={0}
              y1={y}
              x2={W}
              y2={y}
              stroke={db === 0 ? '#3A4A66' : '#1F2838'}
              strokeWidth={db === 0 ? 1.5 : 1}
            />
          );
        })}

        {[100, 1000, 10000].map((fMark) => {
          const x = logX(fMark);
          return <line key={fMark} x1={x} y1={0} x2={x} y2={H} stroke="#1F2838" strokeWidth={1} />;
        })}

        <path d={fillPath} fill="url(#eqFillGrad)" />
        <path
          d={linePath}
          fill="none"
          stroke="#00D4FF"
          strokeWidth={2.5}
          strokeLinecap="round"
          strokeLinejoin="round"
        />

        {nodes.map((node, idx) => (
          <g key={idx}>
            <circle cx={node.x} cy={node.y} r={5.5} fill="#131822" />
            <circle cx={node.x} cy={node.y} r={3.5} fill="#22C55E" />
          </g>
        ))}
      </svg>

      <div className="mt-1 flex items-center justify-between text-[10px] font-mono-tabular text-[#64748B]">
        {['20 Hz', '100 Hz', '1 kHz', '10 kHz', '20 kHz'].map((lbl) => (
          <span key={lbl}>{lbl}</span>
        ))}
      </div>
    </div>
  );
});

EqualizerResponseGraph.displayName = 'EqualizerResponseGraph';
