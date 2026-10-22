import { useCallback, useEffect, useState } from 'react'
import type { Grouping } from '../domain/money'

// The same list as the Android app's More screen.
export const CURRENCIES: { code: string; symbol: string; name: string; grouping: Grouping }[] = [
  { code: 'BDT', symbol: '৳', name: 'Bangladeshi taka', grouping: 'SOUTH_ASIAN' },
  { code: 'INR', symbol: '₹', name: 'Indian rupee', grouping: 'SOUTH_ASIAN' },
  { code: 'PKR', symbol: 'Rs', name: 'Pakistani rupee', grouping: 'SOUTH_ASIAN' },
  { code: 'NPR', symbol: 'Rs', name: 'Nepalese rupee', grouping: 'SOUTH_ASIAN' },
  { code: 'LKR', symbol: 'Rs', name: 'Sri Lankan rupee', grouping: 'INTERNATIONAL' },
  { code: 'USD', symbol: '$', name: 'US dollar', grouping: 'INTERNATIONAL' },
  { code: 'EUR', symbol: '€', name: 'Euro', grouping: 'INTERNATIONAL' },
  { code: 'GBP', symbol: '£', name: 'British pound', grouping: 'INTERNATIONAL' },
  { code: 'SAR', symbol: 'SAR', name: 'Saudi riyal', grouping: 'INTERNATIONAL' },
  { code: 'AED', symbol: 'AED', name: 'UAE dirham', grouping: 'INTERNATIONAL' },
  { code: 'QAR', symbol: 'QAR', name: 'Qatari riyal', grouping: 'INTERNATIONAL' },
  { code: 'KWD', symbol: 'KWD', name: 'Kuwaiti dinar', grouping: 'INTERNATIONAL' },
  { code: 'OMR', symbol: 'OMR', name: 'Omani rial', grouping: 'INTERNATIONAL' },
  { code: 'MYR', symbol: 'RM', name: 'Malaysian ringgit', grouping: 'INTERNATIONAL' },
  { code: 'SGD', symbol: 'S$', name: 'Singapore dollar', grouping: 'INTERNATIONAL' },
  { code: 'CAD', symbol: 'C$', name: 'Canadian dollar', grouping: 'INTERNATIONAL' },
  { code: 'AUD', symbol: 'A$', name: 'Australian dollar', grouping: 'INTERNATIONAL' },
  { code: 'JPY', symbol: '¥', name: 'Japanese yen', grouping: 'INTERNATIONAL' },
  { code: 'TRY', symbol: '₺', name: 'Turkish lira', grouping: 'INTERNATIONAL' },
  { code: 'IDR', symbol: 'Rp', name: 'Indonesian rupiah', grouping: 'INTERNATIONAL' },
]

export type ThemeMode = 'system' | 'light' | 'dark'

export const THEMES: { value: ThemeMode; label: string }[] = [
  { value: 'system', label: 'System default' },
  { value: 'light', label: 'Light' },
  { value: 'dark', label: 'Dark' },
]

const THEME_KEY = 'fintrack.theme'

function readTheme(): ThemeMode {
  try {
    const v = localStorage.getItem(THEME_KEY)
    return v === 'light' || v === 'dark' ? v : 'system'
  } catch {
    return 'system'
  }
}

export function applyTheme(mode: ThemeMode = readTheme()) {
  if (mode === 'system') document.documentElement.removeAttribute('data-theme')
  else document.documentElement.setAttribute('data-theme', mode)
}

/** Theme is per browser, like the phone's: one device can be dark while the other is light. */
export function useTheme(): [ThemeMode, (mode: ThemeMode) => void] {
  const [mode, setMode] = useState<ThemeMode>(readTheme)
  useEffect(() => applyTheme(mode), [mode])
  const set = useCallback((m: ThemeMode) => {
    try {
      localStorage.setItem(THEME_KEY, m)
    } catch {
      // Storage blocked: the choice lasts for this visit.
    }
    setMode(m)
  }, [])
  return [mode, set]
}
