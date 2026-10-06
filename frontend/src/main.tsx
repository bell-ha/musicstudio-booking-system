import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router'
import { GoogleCallbackPage } from './pages/GoogleCallbackPage'
import { InvitationPage } from './pages/InvitationPage'
import { InvitePage } from './pages/InvitePage'
import { JoinCodePage } from './pages/JoinCodePage'
import { JoinRequestPage } from './pages/JoinRequestPage'
import { LoginPage } from './pages/LoginPage'
import { MembersPage } from './pages/MembersPage'
import { NewOrganizationPage } from './pages/NewOrganizationPage'
import { OrganizationHomePage } from './pages/OrganizationHomePage'
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
        <Route path="/auth/callback/google" element={<GoogleCallbackPage />} />
        <Route path="/" element={<RequireLogin><OrganizationsPage /></RequireLogin>} />
        <Route path="/organizations/new" element={<RequireLogin><NewOrganizationPage /></RequireLogin>} />
        <Route path="/invite" element={<InvitePage />} />
        <Route path="/join" element={<RequireLogin><JoinRequestPage /></RequireLogin>} />
        <Route path="/orgs/:orgId" element={<RequireLogin><OrganizationHomePage /></RequireLogin>} />
        <Route path="/orgs/:orgId/members" element={<RequireLogin><MembersPage /></RequireLogin>} />
        <Route path="/orgs/:orgId/invite" element={<RequireLogin><InvitationPage /></RequireLogin>} />
        <Route path="/orgs/:orgId/join-code" element={<RequireLogin><JoinCodePage /></RequireLogin>} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  </StrictMode>,
)
