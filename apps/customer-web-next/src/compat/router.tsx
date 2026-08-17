"use client";

import NextLink from "next/link";
import { useParams, usePathname, useRouter, useSearchParams } from "next/navigation";
import { useCallback, type AnchorHTMLAttributes, type ReactNode } from "react";

type SearchValue = string | number | boolean | null | undefined;
type NavigateOptions = {
  to: string;
  params?: Record<string, SearchValue>;
  search?: Record<string, SearchValue>;
  replace?: boolean;
};

function interpolatePath(to: string, params?: Record<string, SearchValue>): string {
  let result = to;
  for (const [key, value] of Object.entries(params ?? {})) {
    result = result.replace(`$${key}`, encodeURIComponent(String(value ?? "")));
  }
  return result;
}

function withSearch(path: string, search?: Record<string, SearchValue>): string {
  const query = new URLSearchParams();
  for (const [key, value] of Object.entries(search ?? {})) {
    if (value !== null && value !== undefined && value !== "") query.set(key, String(value));
  }
  const encoded = query.toString();
  return encoded ? `${path}?${encoded}` : path;
}

type LinkProps = Omit<AnchorHTMLAttributes<HTMLAnchorElement>, "href"> & {
  to: string;
  params?: Record<string, SearchValue>;
  search?: Record<string, SearchValue>;
  children?: ReactNode;
};

export function Link({ to, params, search, children, ...props }: LinkProps) {
  return (
    <NextLink href={withSearch(interpolatePath(to, params), search)} {...props}>
      {children}
    </NextLink>
  );
}

export function useNavigate() {
  const router = useRouter();
  return useCallback(({ to, params, search, replace }: NavigateOptions) => {
    const href = withSearch(interpolatePath(to, params), search);
    if (replace) router.replace(href);
    else router.push(href);
  }, [router]);
}

export function useRouterState<T>({ select }: { select: (state: { location: { pathname: string } }) => T }): T {
  const pathname = usePathname();
  return select({ location: { pathname } });
}

export function getRouteApi(_path: string) {
  void _path;
  return {
    useParams: () => useParams<Record<string, string>>(),
    useSearch: () => {
      const params = useSearchParams();
      return Object.fromEntries(params.entries()) as Record<string, string | undefined>;
    },
  };
}
