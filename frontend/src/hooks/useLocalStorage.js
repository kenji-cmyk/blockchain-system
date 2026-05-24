import { useCallback, useState } from "react";

function useLocalStorage(key, initialValue) {
  const [value, setValue] = useState(() => {
    try {
      return JSON.parse(window.localStorage.getItem(key)) || initialValue;
    } catch {
      return initialValue;
    }
  });

  const setStoredValue = useCallback(
    (nextValue) => {
      setValue((current) => {
        const resolved = typeof nextValue === "function" ? nextValue(current) : nextValue;
        window.localStorage.setItem(key, JSON.stringify(resolved));
        return resolved;
      });
    },
    [key]
  );

  return [value, setStoredValue];
}

export { useLocalStorage };
