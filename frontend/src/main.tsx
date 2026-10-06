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
import { SchedulePage } from './pages/academy/SchedulePage'
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
import { OrgLayout } from './OrgLayout'
import { NoticesPage } from './pages/site/NoticesPage'
import { PublicSitePage } from './pages/site/PublicSitePage'
import { SiteSettingsPage } from './pages/site/SiteSettingsPage'
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
        <Route path="/s/:slug" element={<PublicSitePage />} />
        <Route path="/join" element={<RequireLogin><JoinRequestPage /></RequireLogin>} />
        <Route path="/orgs/:orgId" element={<RequireLogin><OrgLayout /></RequireLogin>}>
          <Route index element={<OrganizationHomePage />} />
          <Route path="members" element={<MembersPage />} />
          <Route path="invite" element={<InvitationPage />} />
          <Route path="join-code" element={<JoinCodePage />} />
          <Route path="notices" element={<NoticesPage />} />
          <Route path="site" element={<SiteSettingsPage />} />
          <Route path="academy/catalog" element={<CatalogPage />} />
          <Route path="academy/students" element={<StudentsPage />} />
          <Route path="academy/my-students" element={<StudentsPage mine />} />
          <Route path="academy/students/new" element={<StudentFormPage />} />
          <Route path="academy/students/:studentId" element={<StudentDetailPage />} />
          <Route path="academy/students/:studentId/edit" element={<StudentFormPage />} />
          <Route path="academy/me" element={<MyLessonsPage />} />
          <Route path="academy/schedule" element={<SchedulePage />} />
          <Route path="practice" element={<PracticeMapPage />} />
          <Route path="practice/my" element={<MyBookingsPage />} />
          <Route path="practice/bookings" element={<AdminBookingsPage />} />
          <Route path="practice/floors" element={<FloorEditorPage />} />
          <Route path="practice/rooms" element={<RoomsPage />} />
          <Route path="practice/policy" element={<PolicyPage />} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  </StrictMode>,
)
