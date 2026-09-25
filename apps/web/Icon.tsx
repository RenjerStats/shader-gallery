type Name='arrow'|'plus'|'heart'|'bookmark'|'check'|'share'|'phone'|'qr'|'pause'|'play'|'search'|'close'|'reset';
const paths:Record<Name,string>={
  arrow:'M4 12h16m-6-6 6 6-6 6', plus:'M12 5v14M5 12h14',
  heart:'M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1.1-1.1a5.5 5.5 0 0 0-7.8 7.8L12 21l8.8-8.6a5.5 5.5 0 0 0 0-7.8Z',
  bookmark:'M6 3h12v18l-6-4-6 4Z', check:'m5 12 4 4L19 6',
  share:'M12 16V3m-5 5 5-5 5 5M5 13v7h14v-7', phone:'M7 2h10a1 1 0 0 1 1 1v18a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V3a1 1 0 0 1 1-1Zm3 17h4',
  qr:'M3 3h6v6H3Zm12 0h6v6h-6ZM3 15h6v6H3Zm12 0h2v2h-2Zm6 0v6h-6v-2',
  pause:'M9 5v14M15 5v14', play:'m8 4 12 8-12 8Z', search:'m16 16 5 5M18 10.5a7.5 7.5 0 1 1-15 0 7.5 7.5 0 0 1 15 0',
  close:'m6 6 12 12M6 18 18 6', reset:'M3 4v6h6M4 10a8 8 0 1 1 1 8'
};
export function Icon({name,filled=false}:{name:Name;filled?:boolean}){return <svg className="icon" viewBox="0 0 24 24" fill={filled?'currentColor':'none'} stroke="currentColor" strokeWidth="1.65" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d={paths[name]}/></svg>}
