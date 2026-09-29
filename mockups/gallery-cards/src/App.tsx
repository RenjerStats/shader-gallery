import {useEffect, useState} from 'react';
import {DesktopMinimal} from './views/DesktopMinimal';
import {DesktopGallery} from './views/DesktopGallery';
import {MobileMinimal} from './views/MobileMinimal';
import {MobileGallery} from './views/MobileGallery';
import {PhoneShell, PhoneScreen} from './PhoneShell';
import './styles/base.css';

type Mode = 'web-minimal' | 'web-gallery' | 'app-minimal' | 'app-gallery';

const modes: {id: Mode; label: string; hint: string}[] = [
  {id: 'web-minimal', label: 'A · Веб', hint: 'Минимализм: изображение шейдера плавно перетекает в карточку.'},
  {id: 'web-gallery', label: 'B · Веб', hint: '3D-зал на Three.js: длинная стена галереи, прокрутка мышью, клик — рассмотреть.'},
  {id: 'app-minimal', label: 'A · Мобилка', hint: 'Карточку можно покрутить в руках: пальцем или гироскопом.'},
  {id: 'app-gallery', label: 'B · Мобилка', hint: 'Тот же 3D-зал в телефоне: палец — прогулка, наклон — осмотреться.'}
];

function useNarrow() {
  const [narrow, setNarrow] = useState(() => matchMedia('(max-width: 760px)').matches);
  useEffect(() => {
    const mq = matchMedia('(max-width: 760px)');
    const on = () => setNarrow(mq.matches);
    mq.addEventListener('change', on);
    return () => mq.removeEventListener('change', on);
  }, []);
  return narrow;
}

export default function App() {
  const [mode, setMode] = useState<Mode>('web-minimal');
  const narrow = useNarrow();

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const i = ['1', '2', '3', '4'].indexOf(e.key);
      if (i >= 0) setMode(modes[i].id);
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, []);

  const isApp = mode.startsWith('app');
  const hint = modes.find((m) => m.id === mode)!.hint;

  return (
    <>
      {mode === 'web-minimal' && <DesktopMinimal />}
      {mode === 'web-gallery' && <DesktopGallery />}
      {mode === 'app-minimal' &&
        (narrow ? (
          <PhoneScreen><MobileMinimal /></PhoneScreen>
        ) : (
          <PhoneShell><MobileMinimal /></PhoneShell>
        ))}
      {mode === 'app-gallery' &&
        (narrow ? (
          <PhoneScreen dark="#140d07"><MobileGallery /></PhoneScreen>
        ) : (
          <PhoneShell dark="#140d07"><MobileGallery /></PhoneShell>
        ))}

      <div className="switcher" role="tablist" aria-label="Версии макета">
        {modes.map((m, i) => (
          <button
            key={m.id}
            role="tab"
            aria-selected={mode === m.id}
            data-active={mode === m.id ? 'true' : undefined}
            onClick={() => setMode(m.id)}
          >
            <span>{m.label}</span>
          </button>
        ))}
        <div className="divider" />
        <div className="hint">{isApp ? hint : hint + ' Наведите/потяните карточку.'}</div>
      </div>
    </>
  );
}