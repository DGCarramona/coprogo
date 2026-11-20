INSERT INTO members (email, created_at)
SELECT recipients.invited_email, CURRENT_TIMESTAMP
FROM (
    SELECT DISTINCT invited_email
    FROM group_invitations
    WHERE accepted_at IS NULL
) AS recipients
ON CONFLICT (email) DO NOTHING;

INSERT INTO group_memberships (id, "group", member_email, joined_at)
SELECT gen_random_uuid(), invitation."group", invitation.invited_email, CURRENT_TIMESTAMP
FROM group_invitations invitation
WHERE invitation.accepted_at IS NULL
ON CONFLICT ("group", member_email) DO NOTHING;