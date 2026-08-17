import { PolicyList, PolicySection, PublicPolicyPage } from "@/components/legal/PublicPolicyPage";

export const metadata = {
  title: "Privacy Policy | Craves",
  description: "How Craves handles customer account, order, delivery, and payment-reference data.",
};

export default function PrivacyPage() {
  return (
    <PublicPolicyPage
      eyebrow="Privacy policy"
      title="How Craves handles your information."
      intro="Effective 15 August 2026. This notice describes the main information Craves processes to provide marketplace, account, order, payment, refund, support, and delivery functions."
    >
      <PolicySection title="Information used by Craves">
        <PolicyList>
          <li>Account and identity information such as phone number, name, email when provided, roles, and secure session identifiers.</li>
          <li>Saved delivery addresses and the order-specific address snapshot needed to fulfil an order.</li>
          <li>Cart, checkout, order, meal-plan, chef, delivery, notification, and support information.</li>
          <li>Payment and refund references, amounts, currencies, provider statuses, and reconciliation records.</li>
          <li>Security, audit, diagnostic, and operational records needed to protect and operate the service.</li>
        </PolicyList>
      </PolicySection>

      <PolicySection title="Payments">
        <p>
          Craves uses Razorpay for hosted payment processing. Craves stores payment and refund references and verified financial status needed for orders and reconciliation, but the Craves application does not require customers to provide card CVVs or UPI PINs to Craves.
        </p>
      </PolicySection>

      <PolicySection title="Service providers">
        <p>
          Craves shares only the information needed for a service provider to perform its role. Current platform integrations include Firebase for phone authentication, Razorpay for payments and refunds, supported delivery providers for fulfilment, Microsoft Azure for application infrastructure, and communication services for transactional messages.
        </p>
      </PolicySection>

      <PolicySection title="Security and access">
        <PolicyList>
          <li>Protected business APIs require Craves authentication and resource-ownership checks.</li>
          <li>Provider credentials and internal service secrets are not placed in public web or mobile code.</li>
          <li>Financial and operational events are retained with audit and reconciliation controls appropriate to their purpose.</li>
          <li>Craves masks secrets and sensitive personal data from application telemetry wherever it is not required.</li>
        </PolicyList>
      </PolicySection>

      <PolicySection title="Questions or requests">
        <p>
          For privacy or account questions, contact{" "}
          <a className="font-semibold text-[#F62E18] underline" href="mailto:support@craves.in">support@craves.in</a>.
          Craves may need to retain certain transaction, refund, security, or audit records where required for legitimate operational, accounting, dispute, or legal purposes.
        </p>
      </PolicySection>
    </PublicPolicyPage>
  );
}
