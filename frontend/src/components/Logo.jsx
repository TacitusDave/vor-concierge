const LOGO_SRC = '/VOR%20Updated%20Logo.png';

/**
 * The brand mark + wordmark lockup, icon-left / text-right per the approved layout. Used in the
 * app navbar and the login card; will also anchor the Phase 4 marketing site header.
 */
export function Logo({ iconSize = 'h-8 w-8', textSize = 'text-lg', tagline }) {
  return (
    <div className="flex items-center gap-2.5">
      <img
        src={LOGO_SRC}
        alt="VOR Concierge"
        className={`${iconSize} shrink-0 drop-shadow-[0_0_10px_rgba(16,185,129,0.35)]`}
      />
      <div className="leading-tight">
        <span className={`${textSize} font-bold tracking-tight text-slate-100`}>
          VOR <span className="text-brand-400">Concierge</span>
        </span>
        {tagline && <p className="text-sm text-slate-500">{tagline}</p>}
      </div>
    </div>
  );
}
