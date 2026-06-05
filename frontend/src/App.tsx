import { Routes, Route } from 'react-router-dom';
import Layout from './components/Layout';
import HomePage from './pages/HomePage';
import ProjectPage from './pages/ProjectPage';
import ScriptPage from './pages/ScriptPage';

function App() {
  return (
    <Layout>
      <Routes>
        <Route path="/" element={<HomePage />} />
        <Route path="/project/:id" element={<ProjectPage />} />
        <Route path="/project/:id/script/:scriptId" element={<ScriptPage />} />
      </Routes>
    </Layout>
  );
}

export default App;
