import type { User } from 'firebase/auth'
import { signOut } from 'firebase/auth'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { buildBackup, buildCsv, download } from '../../data/export'
import { store, useData } from '../../data/store'
import { toDateInput } from '../../domain/dates'
import { formatMoney, type Grouping } from '../../domain/money'
import { useFirebase } from '../App'
import { useToast } from '../common'
import { ChoiceDialog } from '../dialogs'
import { Icon, type IconName } from '../icons'
import { CURRENCIES, THEMES, useTheme, type ThemeMode } from '../preferences'

type Dialog = 'currency' | 'grouping' | 'theme' | null

export function MorePage({ user }: { user: User }) {
  const data = useData()
  const { auth } = useFirebase()
  const navigate = useNavigate()
  const toast = useToast()
  const [theme, setTheme] = useTheme()
  const [dialog, setDialog] = useState<Dialog>(null)
  const s = data.settings
  const today = toDateInput(Date.now())

  const syncText = data.error
    ? `Problem: ${data.error}`
    : data.pending > 0 ? `Saving ${data.pending} change${data.pending === 1 ? '' : 's'}…`
      : data.synced ? 'Up to date with your phone' : 'Connecting…'
  const syncColor = data.error ? 'var(--error)' : data.pending > 0 || !data.synced ? 'var(--warning)' : 'var(--income)'

  return (
    <div className="page">
      <div className="page-head"><h1>More</h1></div>
      <div className="grid-2">
        <div className="stack">
          <Group title="Account">
            <div className="item">
              {user.photoURL
                ? <img src={user.photoURL} alt="" width={40} height={40} style={{ borderRadius: '50%' }} referrerPolicy="no-referrer" />
                : <span className="badge" style={{ width: 40, height: 40, background: 'var(--secondary-container)' }}>{(user.displayName ?? user.email ?? '?')[0]}</span>}
              <div className="grow">
                <div className="title">{user.displayName ?? 'Signed in'}</div>
                <div className="small muted">{user.email}</div>
              </div>
            </div>
            <div className="item">
              <Icon name="cloud" />
              <div className="grow">
                <div className="title">Sync</div>
                <div className="small muted"><span className="sync-dot" style={{ background: syncColor }} /> {syncText}</div>
              </div>
            </div>
            <Row icon="logout" title="Sign out" subtitle="Your data stays in your account and on your phone" onClick={() => signOut(auth)} />
          </Group>

          <Group title="Manage">
            <Row icon="wallet" title="Accounts" subtitle="Cash, bank, mobile wallets" onClick={() => navigate('/accounts')} />
            <Row icon="tag" title="Categories" subtitle="Add, rename, recolour" onClick={() => navigate('/categories')} />
            <Row icon="repeat" title="Recurring" subtitle="Rent, salary and other repeating entries" onClick={() => navigate('/recurring')} />
          </Group>
        </div>

        <div className="stack">
          <Group title="Preferences">
            <Row icon="currency" title="Currency" subtitle={`${s.currencyCode} (${s.currencySymbol}) · also changes it on your phone`} onClick={() => setDialog('currency')} />
            <Row icon="hash" title="Number format" subtitle={formatMoney(123456700, s.currencySymbol, s.grouping)} onClick={() => setDialog('grouping')} />
            <Row icon="palette" title="Theme" subtitle={THEMES.find(t => t.value === theme)?.label ?? ''} onClick={() => setDialog('theme')} />
          </Group>

          <Group title="Your data">
            <Row icon="sheet" title="Export to CSV" subtitle="Open your history in Excel or Google Sheets"
              onClick={() => { download(`fintrack-transactions-${today}.csv`, buildCsv(data), 'text/csv'); toast(`Exported ${data.transactions.length} transactions`) }} />
            <Row icon="download" title="Download a backup" subtitle="A file the Android app can restore"
              onClick={() => { download(`fintrack-backup-${today}.json`, buildBackup(data), 'application/json'); toast('Backup downloaded') }} />
          </Group>

          <Group title="About">
            <Row icon="phone" title="FinTrack for Android" subtitle="Get the phone app; sign in there with the same Google account"
              onClick={() => window.open('https://github.com/ocpabdurrahim/fintrack-android/releases/latest', '_blank', 'noopener')} />
          </Group>
        </div>
      </div>

      {dialog === 'currency' && (
        <ChoiceDialog
          title="Currency" value={s.currencyCode}
          options={CURRENCIES.map(c => ({ value: c.code, label: `${c.symbol}  ${c.name}`, hint: c.code }))}
          onPick={code => {
            const c = CURRENCIES.find(x => x.code === code)!
            store.setSettings({ currencyCode: c.code, currencySymbol: c.symbol, grouping: c.grouping })
          }}
          onClose={() => setDialog(null)}
        />
      )}
      {dialog === 'grouping' && (
        <ChoiceDialog<Grouping>
          title="Number format" value={s.grouping}
          options={(['SOUTH_ASIAN', 'INTERNATIONAL'] as Grouping[]).map(g => ({ value: g, label: formatMoney(123456700, s.currencySymbol, g) }))}
          onPick={grouping => store.setSettings({ ...s, grouping })}
          onClose={() => setDialog(null)}
        />
      )}
      {dialog === 'theme' && (
        <ChoiceDialog<ThemeMode> title="Theme" value={theme} options={THEMES} onPick={setTheme} onClose={() => setDialog(null)} />
      )}
    </div>
  )
}

function Group({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div>
      <div className="section-title" style={{ color: 'var(--primary)', fontSize: 14 }}><span>{title}</span></div>
      <div className="card" style={{ overflow: 'hidden' }}>{children}</div>
    </div>
  )
}

function Row({ icon, title, subtitle, onClick }: { icon: IconName; title: string; subtitle: string; onClick: () => void }) {
  return (
    <button className="item" onClick={onClick}>
      <Icon name={icon} />
      <div className="grow">
        <div className="title">{title}</div>
        <div className="small muted">{subtitle}</div>
      </div>
      <Icon name="right" size={18} className="muted" />
    </button>
  )
}
