type Props = {name: string; size?: number; className?: string};

const paths: Record<string, string> = {
  eye: 'M1.5 10s3.2-5.5 8.5-5.5S18.5 10 18.5 10s-3.2 5.5-8.5 5.5S1.5 10 1.5 10Z|M10 7.6a2.4 2.4 0 1 0 0 4.8 2.4 2.4 0 0 0 0-4.8Z',
  heart: 'M10 16.8S2.8 12.6 2.8 8.1A3.9 3.9 0 0 1 10 5.9a3.9 3.9 0 0 1 7.2 2.2c0 4.5-7.2 8.7-7.2 8.7Z',
  play: 'M6.5 4.6 15 10l-8.5 5.4Z',
  search: 'M9 3.2a5.8 5.8 0 1 0 0 11.6 5.8 5.8 0 0 0 0-11.6Z|M13.4 13.4 17.5 17.5',
  home: 'M3.2 8.9 10 3.4l6.8 5.5v7.1a.9.9 0 0 1-.9.9h-3.7v-5h-4.4v5H4.1a.9.9 0 0 1-.9-.9Z',
  layers: 'M10 2.8 17.6 7 10 11.2 2.4 7Z|M2.4 11 10 15.2 17.6 11',
  users: 'M7.2 9.2a2.7 2.7 0 1 0 0-5.4 2.7 2.7 0 0 0 0 5.4Z|M2.2 16.6c0-2.8 2.2-4.6 5-4.6s5 1.8 5 4.6|M13.4 4.2a2.7 2.7 0 0 1 0 5.2|M14 12.2c2.3.3 3.8 1.9 3.8 4.4',
  user: 'M10 9.6a3.1 3.1 0 1 0 0-6.2 3.1 3.1 0 0 0 0 6.2Z|M3.6 17c0-3.2 2.9-5.1 6.4-5.1s6.4 1.9 6.4 5.1',
  compass: 'M10 2.6a7.4 7.4 0 1 0 0 14.8 7.4 7.4 0 0 0 0-14.8Z|M12.9 7.1l-1.7 4.1-4.1 1.7 1.7-4.1Z',
  more: 'M4.6 10h.1|M9.9 10h.1|M15.2 10h.1',
  filter: 'M3.2 5.6h13.6|M5.6 10h8.8|M8.2 14.4h3.6',
  grid: 'M3.4 3.4h5.2v5.2H3.4Z|M11.4 3.4h5.2v5.2h-5.2Z|M3.4 11.4h5.2v5.2H3.4Z|M11.4 11.4h5.2v5.2h-5.2Z',
  back: 'M12.4 4.4 6.6 10l5.8 5.6',
  share: 'M10 12.8V3.2|M6.6 6.4 10 3.2l3.4 3.2|M4.4 11.2v4.2a1.2 1.2 0 0 0 1.2 1.2h8.8a1.2 1.2 0 0 0 1.2-1.2v-4.2',
  download: 'M10 3.2v8.8|M6.6 8.8 10 12.2l3.4-3.4|M4.4 15.2h11.2',
  expand: 'M11.6 3.6h4.8v4.8|M8.4 16.4H3.6v-4.8|M16.4 3.6 11 9|M3.6 16.4 9 11',
  close: 'M5.2 5.2 14.8 14.8|M14.8 5.2 5.2 14.8',
  sparkle: 'M10 2.8 11.7 8 17 9.7 11.7 11.4 10 16.6 8.3 11.4 3 9.7 8.3 8Z'
};

export function Icon({name, size = 18, className}: Props) {
  const d = paths[name] ?? paths.sparkle;
  return (
    <svg
      className={className}
      width={size}
      height={size}
      viewBox="0 0 20 20"
      fill={name === 'play' ? 'currentColor' : 'none'}
      stroke="currentColor"
      strokeWidth={name === 'more' ? 2.6 : 1.5}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {d.split('|').map((p, i) => (
        <path key={i} d={p} />
      ))}
    </svg>
  );
}