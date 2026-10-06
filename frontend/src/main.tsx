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
import { CatalogPage } from './pages/academy/CatalogPage'
import { MyLessonsPage } from './pages/academy/MyLessonsPage'
import { StudentDetailPage } from './pages/academy/StudentDetailPage'
import { StudentFormPage } from './pages/academy/StudentFormPage'
import { StudentsPage } from './pages/academy/StudentsPage'
import { AdminBookingsPage } from './pages/practice/AdminBookingsPage'
import { FloorEditorPage } from './pages/practice/FloorEditorPage'
import { MyBookingsPage } from './pages/practice/MyBookingsPage'
import { PracticeMapPage } from './pages/practice/PracticeMapPage'
import { PolicyPage } from './pages/practice/PolicyPage'
import { RoomsPage } from './pages/practice/RoomsPage'
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
        <Route path="/orgs/:orgId/academy/catalog" element={<RequireLogin><CatalogPage /></RequireLogin>} />
        <Route path="/orgs/:orgId/academy/students" element={<RequireLogin><StudentsPage /></RequireLogin>} />
        <Route path="/orgs/:orgId/academy/my-students" element={<RequireLogin><StudentsPage mine /></RequireLogin>} />
        <Route path="/orgs/:orgId/academy/students/new" element={<RequireLogin><StudentFormPage /></RequireLogin>} />
        <Route path="/orgs/:orgId/academy/students/:studentId" element={<RequireLogin><StudentDetailPage /></RequireLogin>} />
        <Route path="/orgs/:orgId/academy/students/:studentId/edit" element={<RequireLogin><StudentFormPage /></RequireLogin>} />
        <Route path="/orgs/:orgId/academy/me" element={<RequireLogin><MyLessonsPage /></RequireLogin>} />
        <Route path="/orgs/:orgId/practice" element={<RequireLogin><PracticeMapPage /></RequireLogin>} />
        <Route path="/orgs/:orgId/practice/my" element={<RequireLogin><MyBookingsPage /></RequireLogin>} />
        <Route path="/orgs/:orgId/practice/bookings" element={<RequireLogin><AdminBookingsPage /></RequireLogin>} />
        <Route path="/orgs/:orgId/practice/floors" element={<RequireLogin><FloorEditorPage /></RequireLogin>} />
        <Route path="/orgs/:orgId/practice/rooms" element={<RequireLogin><RoomsPage /></RequireLogin>} />
        <Route path="/orgs/:orgId/practice/policy" element={<RequireLogin><PolicyPage /></RequireLogin>} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  </StrictMode>,
)
