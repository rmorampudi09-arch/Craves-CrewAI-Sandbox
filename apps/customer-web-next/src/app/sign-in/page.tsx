import { PhoneAuthForm } from "@/components/phone-auth-form";
import { safeReturnPath } from "@/lib/auth-contract";

export const metadata = {
  title: "Sign in | Craves",
  robots: { index: false, follow: false }
};

export default async function SignInPage({
  searchParams
}: {
  searchParams: Promise<{ returnTo?: string }>;
}) {
  const query = await searchParams;
  return (
    <main className="mx-auto flex min-h-screen max-w-xl items-center px-5 py-12">
      <PhoneAuthForm returnTo={safeReturnPath(query.returnTo)} />
    </main>
  );
}
