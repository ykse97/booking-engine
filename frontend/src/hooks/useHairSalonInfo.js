import { useEffect, useState } from 'react';
import { publicApi } from '../api/publicApi';

let cachedInfo = null;
let pendingRequest = null;

export default function useHairSalonInfo() {
    const [info, setInfo] = useState(cachedInfo);

    useEffect(() => {
        let active = true;

        if (cachedInfo) {
            return undefined;
        }

        if (!pendingRequest) {
            pendingRequest = publicApi
                .getHairSalon()
                .then((data) => {
                    cachedInfo = data;
                    return data;
                })
                .catch(() => null)
                .finally(() => {
                    pendingRequest = null;
                });
        }

        pendingRequest
            .then((data) => {
                if (active && data) setInfo(data);
            })
            .catch(() => {
                // ignore; fallback handled by callers
            });

        return () => {
            active = false;
        };
    }, []);

    return info;
}
