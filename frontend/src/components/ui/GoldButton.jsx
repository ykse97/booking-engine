import { motion } from 'framer-motion';

export default function GoldButton({ children, className = '', ...rest }) {
    return (
        <motion.button
            whileHover={{ scale: 1.02 }}
            whileTap={{ scale: 0.98 }}
            className={`btn-gold ${className}`}
            {...rest}
        >
            {children}
        </motion.button>
    );
}
