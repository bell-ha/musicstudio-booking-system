import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router'
import { LoginPage } from './pages/LoginPage'
import { NewOrganizationPage } from './pages/NewOrganizationPage'
import { OrganizationsPage } from './pages/OrganizationsPage'
import { SignupPage } from './pages/SignupPage'
import { RequireLogin } from './RequireLogin'
import './styles/tokens.css'
import './styles/base.css'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <Routes>
        <Route path="/signup" element={<SignupPage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/" element={<RequireLogin><OrganizationsPage /></RequireLogin>} />
        <Route path="/organizations/new" element={<RequireLogin><NewOrganizationPage /></RequireLogin>} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  </StrictMode>,
)
