ALTER TABLE group_memberships
    ADD COLUMN added_by member_email_address NULL REFERENCES members (email),
    ADD COLUMN recorded_at TIMESTAMPTZ NULL;

INSERT INTO members (email, created_at)
SELECT DISTINCT invitation.invited_email, CURRENT_TIMESTAMP
FROM group_invitations invitation
ON CONFLICT (email) DO NOTHING;

INSERT INTO group_memberships (id, "group", member_email, joined_at)
SELECT gen_random_uuid(), invitation."group", invitation.invited_email, invitation.accepted_at
FROM group_invitations invitation
WHERE invitation.accepted_at IS NOT NULL
ON CONFLICT ("group", member_email) DO NOTHING;

UPDATE group_memberships membership
SET added_by = invitation.invited_by,
    recorded_at = invitation.invited_at
FROM group_invitations invitation
WHERE membership."group" = invitation."group"
  AND membership.member_email = invitation.invited_email
  AND membership.added_by IS NULL;

DROP TABLE group_invitations;