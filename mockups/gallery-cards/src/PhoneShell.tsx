import {useEffect, useRef, useState, type ReactNode} from 'react';
import {Icon} from './Icon';
import {requestGyro} from './Tilt';

/** Строка состояния iOS — чисто декоративная, для правдоподобия макета. */
export function StatusBar() {
  return (
    <div className="m-status">
      <span className="m-time">9:41</span>
      <div className="m-status-icons" aria-hidden="true">
        <svg width="18" height="11" viewBox="0 0 18 11" fill="currentColor">
          <rect x="0" y="7" width="3" height="4" rx="1" />
          <rect x="4.5" y="5" width="3" height="6" rx="1" />
          <rect x="9" y="2.5" width="3" height="8.5" rx="1" />
          <rect x="13.5" y="0" width="3" height="11" rx="1" />
        </svg>
        <svg width="16" height="11" viewBox="0 0 16 11" fill="currentColor">
          <path d="M8 10.6 6.1 8.5a2.8 2.8 0 0 1 3.8 0ZM3.7 6.1 2 4.3a8.8 8.8 0 0 1 12 0l-1.7 1.8a6.4 6.4 0 0 0-8.6 0ZM5.7 8.2 4.1 6.5a5.6 5.6 0 0 1 7.8 0l-1.6 1.7a3.3 3.3 0 0 0-4.6 0Z" />
        </svg>
        <svg width="25" height="12" viewBox="0 0 25 12" fill="none">
          <rect x="0.6" y="0.6" width="20.5" height="10.8" rx="3.2" stroke="currentColor" strokeOpacity="0.5" />
          <rect x="2.2" y="2.2" width="16" height="7.6" rx="2" fill="currentColor" />
          <path d="M22.6 4v4a2.2 2.2 0 0 0 0-4Z" fill="currentColor" fillOpacity="0.5" />
        </svg>
      </div>
    </div>
  );
}

type NavItem = {icon: string; label: string; active?: boolean};
export function BottomNav({items}: {items: NavItem[]}) {
  return (
    <nav className="m-nav">
      {items.map((it) => (
        <button key={it.label} data-active={it.active ? 'true' : undefined}>
          <Icon name={it.icon} size={21} />
          <span>{it.label}</span>
        </button>
      ))}
    </nav>
  );
}

/** Масштабирование корпуса телефона под высоту окна (только широкий экран). */
function usePhoneScale() {
  const [scale, setScale] = useState(1);
  useEffect(() => {
    const fit = () => {
      const s = Math.min(1, (window.innerHeight - 120) / 848, (window.innerWidth - 40) / 392);
      setScale(Math.max(0.35, s));
    };
    fit();
    window.addEventListener('resize', fit);
    return () => window.removeEventListener('resize', fit);
  }, []);
  return scale;
}

export function PhoneShell({children, dark = '#07070c'}: {children: ReactNode; dark?: string}) {
  const scale = usePhoneScale();
  const screen = useRef<HTMLDivElement>(null);
  return (
    <div className="stage stage--blurred">
      <div className="phone" style={{transform: `scale(${scale})`}}>
        <div className="phone-island" />
        <div className="phone-screen" ref={screen} style={{background: dark}}>
          {children}
        </div>
      </div>
    </div>
  );
}

/** Полноэкранная раскладка для настоящего узкого экрана. */
export function PhoneScreen({children, dark = '#07070c'}: {children: ReactNode; dark?: string}) {
  return (
    <div className="m-fullscreen" style={{background: dark}}>
      {children}
    </div>
  );
}

/** Кнопка включения гироскопа для «покрутить в руках». */
export function GyroButton({onEnable, label = 'Покрутить в руках'}: {onEnable: () => void; label?: string}) {
  const [state, setState] = useState<'idle' | 'on' | 'denied'>('idle');
  return (
    <button
      className="m-gyro"
      data-state={state}
      onClick={async () => {
        const ok = await requestGyro();
        setState(ok ? 'on' : 'denied');
        if (ok) onEnable();
      }}
    >
      <Icon name="compass" size={15} />
      {state === 'on' ? 'Гироскоп включён' : state === 'denied' ? 'Гироскоп недоступен' : label}
    </button>
  );
}