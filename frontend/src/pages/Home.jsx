import { lazy } from 'react';
import DeferredSection from '../components/DeferredSection';
import Hero from '../components/Hero';
import ServicesSection from '../components/ServicesSection';

const EmployeesSection = lazy(() => import('../components/EmployeesSection'));
const GallerySection = lazy(() => import('../components/GallerySection'));

export default function Home() {
    return (
        <div id="top">
            <Hero />
            <ServicesSection limit={3} />
            <DeferredSection
                sectionId="employees"
                placeholderMinHeight={820}
                rootMargin="0px 0px 120px 0px"
            >
                <EmployeesSection sectionId={null} />
            </DeferredSection>
            <DeferredSection
                sectionId="gallery"
                placeholderMinHeight={760}
                rootMargin="0px 0px 180px 0px"
            >
                <GallerySection sectionId={null} />
            </DeferredSection>
        </div>
    );
}
