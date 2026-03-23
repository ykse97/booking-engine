import Hero from '../components/Hero';
import IntroSection from '../components/IntroSection';
import ServicesSection from '../components/ServicesSection';
import BarbersSection from '../components/BarbersSection';
import GallerySection from '../components/GallerySection';

export default function Home() {
    return (
        <div id="top">
            <Hero />
            <IntroSection />
            <ServicesSection limit={3} />
            <BarbersSection />
            <GallerySection />
        </div>
    );
}
