import { PolicyList, PolicySection, PublicPolicyPage } from "@/components/legal/PublicPolicyPage";

export const metadata = {
  title: "Security | Craves",
  description: "Security guidance for Craves customers and payment users.",
};

export default function SecurityPage() {
  return (
    <PublicPolicyPage
      eyebrow="Security"
      title="Protecting Craves accounts and payments."
      intro="Craves separates public customer applications from backend service credentials and verifies payment state on the server. Customers can also help by keeping authentication and payment secrets private."
    >
      <PolicySection title="What Craves will never ask you to send">
        <PolicyList>
          <li>Your UPI PIN.</li>
          <li>Your card CVV.</li>
          <li>Your banking password.</li>
          <li>A Firebase OTP or account password by email.</li>
          <li>Internal Craves API keys, Azure secrets, or provider credentials.</li>
        </PolicyList>
      </PolicySection>

      <PolicySection title="Payment verification">
        <p>
          Payment checkout is hosted by Razorpay. Craves treats payment as final only after server-side provider verification or a verified signed provider event; a browser redirect by itself is not trusted as proof of payment.
        </p>
      </PolicySection>

      <PolicySection title="Report a concern">
        <p>
          If you believe your Craves account, order, or payment was accessed without permission, contact{" "}
          <a className="font-semibold text-[#F62E18] underline" href="mailto:support@craves.in">support@craves.in</a>.
          Include the relevant Craves order reference if available, but do not include payment secrets.
        </p>
      </PolicySection>
    </PublicPolicyPage>
  );
}
