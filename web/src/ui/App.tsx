import {
  getRedirectResult, GoogleAuthProvider, onAuthStateChanged, signInWithCredential, signInWithEmailAndPassword, signInWithPopup,
  signInWithRedirect, signOut, type Auth, type User,
} from 'firebase/auth'
import { createContext, useContext, useEffect, useState } from 'react'
import { NavLink, Navigate, Route, Routes } from 'react-router-dom'
import { store, useData } from '../data/store'
import { initFirebase, type FirebaseHandles } from '../firebase'
import { EditorContext, Spinner, ToastProvider, type EditorRequest } from './common'
import { EditTxnDialog } from './EditTxnDialog'
import { GoogleLogo, Icon, type IconName } from './icons'
import { BudgetsPage } from './pages/Budgets'
import { HistoryPage } from './pages/History'
import { HomePage } from './pages/Home'
import { AccountsPage, CategoriesPage, RecurringPage } from './pages/Manage'
import { MorePage } from './pages/More'
import { StatsPage } from './pages/Stats'
import { useTheme } from './preferences'

const FirebaseContext = createContext<FirebaseHandles | null>(null)

export function useFirebase(): FirebaseHandles {
  const value = useContext(FirebaseContext)
  if (!value) throw new Error('Firebase is not ready')
  return value
}

export function App() {
  useTheme() // applies the saved theme before anything renders
  const [firebase, setFirebase] = useState<FirebaseHandles | null>(null)
  const [error, setError] = useState<string | null>(null)
  useEffect(() => {
    initFirebase().then(setFirebase, e => setError(e instanceof Error ? e.message : String(e)))
  }, [])
  if (error) {
    return <div className="center-screen"><div className="signin"><h1>FinTrack</h1><p>{error}</p></div></div>
  }
  if (!firebase) return <Spinner />
  return (
    <FirebaseContext.Provider value={firebase}>
      <ToastProvider>
        <AuthGate />
      </ToastProvider>
    </FirebaseContext.Provider>
  )
}

function AuthGate() {
  const { auth, db } = useFirebase()
  const [user, setUser] = useState<User | null | undefined>(undefined)
  // The account the cloud just turned away: FinTrack's data is private to one owner.
  const [refused, setRefused] = useState<string | null>(null)
  useEffect(() => onAuthStateChanged(auth, setUser), [auth])
  const uid = user?.uid
  const email = user?.email
  useEffect(() => {
    if (!uid) return
    setRefused(null)
    void store.start(db, uid, () => {
      setRefused(email ?? 'That account')
      void signOut(auth)
    })
    return () => store.stop()
  }, [auth, db, uid, email])
  if (user === undefined) return <Spinner />
  if (!user) return <SignIn refused={refused} />
  return <Shell user={user} />
}

function SignIn({ refused }: { refused: string | null }) {
  const { auth, emulator } = useFirebase()
  const [error, setError] = useState<string | null>(null)
  useEffect(() => {
    // Finishes a redirect sign-in; failures would otherwise be silent.
    getRedirectResult(auth).catch(e => setError(e instanceof Error ? e.message : String(e)))
  }, [auth])
  const signIn = async () => {
    setError(null)
    const provider = new GoogleAuthProvider()
    provider.setCustomParameters({ prompt: 'select_account' })
    try {
      await signInWithPopup(auth, provider)
    } catch (e) {
      const code = (e as { code?: string }).code
      if (code === 'auth/popup-blocked' || code === 'auth/operation-not-supported-in-this-environment') {
        await signInWithRedirect(auth, provider)
      } else if (code !== 'auth/popup-closed-by-user' && code !== 'auth/cancelled-popup-request') {
        setError(e instanceof Error ? e.message : String(e))
      }
    }
  }
  return (
    <div className="center-screen">
      <div className="signin">
        <div className="logo">F</div>
        <h1>FinTrack</h1>
        <p>Your daily income and expenses, the same here as on your phone.</p>
        <button className="google-btn" onClick={signIn}><GoogleLogo />Continue with Google</button>
        {emulator && (
          <div style={{ marginTop: 12 }}>
            <button className="btn text" onClick={() => emulatorOwner(auth).catch(e => setError(String(e)))}>
              Sign in as the owner (emulator)
            </button>
            {/* The auth emulator accepts an unsigned Google token, so a stranger needs no popup either. */}
            <button className="btn text" onClick={() => signInWithCredential(auth, GoogleAuthProvider.credential(
              JSON.stringify({ sub: 'stranger', email: 'stranger@fintrack.test', email_verified: true, name: 'Stranger' }),
            )).catch(e => setError(String(e)))}>
              Sign in as someone else (emulator)
            </button>
          </div>
        )}
        {refused && (
          <p className="error-text small" style={{ marginTop: 16 }}>
            {refused} can't use this FinTrack: it's private to its owner's Google account.
          </p>
        )}
        {error && <p className="error-text small" style={{ marginTop: 16 }}>{error}</p>}
        <p className="small" style={{ marginTop: 28 }}>
          Use the same Google account you signed in with in the Android app.
          {emulator && <><br /><b>Test mode:</b> using the local Firebase emulator.</>}
        </p>
      </div>
    </div>
  )
}

/**
 * The tests' owner account, whose fixed ID firestore.rules lets in. Only the
 * emulator's admin access ("Bearer owner") can create an account with a chosen ID;
 * if it already exists, that call fails and the sign-in just goes ahead.
 */
async function emulatorOwner(auth: Auth) {
  await fetch(`http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/projects/${auth.app.options.projectId}/accounts`, {
    method: 'POST',
    headers: { Authorization: 'Bearer owner', 'Content-Type': 'application/json' },
    body: JSON.stringify({ localId: 'fintrack-test-owner', email: 'owner@fintrack.test', password: 'secret-pass' }),
  })
  await signInWithEmailAndPassword(auth, 'owner@fintrack.test', 'secret-pass')
}

const NAV: { to: string; label: string; icon: IconName }[] = [
  { to: '/', label: 'Home', icon: 'home' },
  { to: '/history', label: 'History', icon: 'history' },
  { to: '/stats', label: 'Stats', icon: 'stats' },
  { to: '/budgets', label: 'Budgets', icon: 'budgets' },
  { to: '/more', label: 'More', icon: 'more' },
]

function Shell({ user }: { user: User }) {
  const data = useData()
  const [editor, setEditor] = useState<EditorRequest | null>(null)
  const syncLabel = data.error ? 'Sync problem' : data.pending > 0 ? 'Saving…' : data.synced ? 'Synced' : 'Connecting…'
  const syncColor = data.error ? 'var(--error)' : data.pending > 0 || !data.synced ? 'var(--warning)' : 'var(--income)'

  return (
    <EditorContext.Provider value={setEditor}>
      <div className="app">
        <nav className="sidebar" aria-label="Main">
          <div className="brand"><span className="logo">F</span>FinTrack</div>
          {NAV.map(n => (
            <NavLink key={n.to} to={n.to} end={n.to === '/'} className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
              <Icon name={n.icon} />{n.label}
            </NavLink>
          ))}
          <button className="btn add-button" onClick={() => setEditor({ type: 'EXPENSE' })}><Icon name="plus" size={18} />Add transaction</button>
          <div className="sidebar-foot">
            <span className="sync-dot" style={{ background: syncColor }} /> {syncLabel}
            <div style={{ overflow: 'hidden', textOverflow: 'ellipsis' }}>{user.email}</div>
          </div>
        </nav>

        <main className="main">
          {!data.ready ? <Spinner /> : (
            <Routes>
              <Route path="/" element={<HomePage />} />
              <Route path="/history" element={<HistoryPage />} />
              <Route path="/stats" element={<StatsPage />} />
              <Route path="/budgets" element={<BudgetsPage />} />
              <Route path="/more" element={<MorePage user={user} />} />
              <Route path="/accounts" element={<AccountsPage />} />
              <Route path="/categories" element={<CategoriesPage />} />
              <Route path="/recurring" element={<RecurringPage />} />
              <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
          )}
        </main>

        <button className="fab" onClick={() => setEditor({ type: 'EXPENSE' })}><Icon name="plus" />Add</button>
        <nav className="bottom-nav" aria-label="Main">
          {NAV.map(n => (
            <NavLink key={n.to} to={n.to} end={n.to === '/'} className={({ isActive }) => (isActive ? 'active' : '')}>
              <span className="pill"><Icon name={n.icon} /></span>{n.label}
            </NavLink>
          ))}
        </nav>
      </div>
      {editor && <EditTxnDialog request={editor} onClose={() => setEditor(null)} />}
    </EditorContext.Provider>
  )
}
