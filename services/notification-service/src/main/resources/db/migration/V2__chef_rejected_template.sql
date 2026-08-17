INSERT INTO notification_schema.notification_template(code, channel, subject_template, body_template)
VALUES
    ('CHEF_REJECTED_IN_APP', 'IN_APP', NULL, 'Your Craves chef profile was not approved. Please review the reason and submit again.')
ON CONFLICT (code) DO NOTHING;
