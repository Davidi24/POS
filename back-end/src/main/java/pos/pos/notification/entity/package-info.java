/**
 * CURRENT RELATION: notification_templates.restaurant_id -> restaurants.id
 * CURRENT RELATION: notification_preferences.user_id -> users.id
 * CURRENT RELATION: notifications.template_id -> notification_templates.id
 * CURRENT RELATION: notifications.recipient_user_id -> users.id
 *
 * FUTURE RELATION: delivery receipts and provider webhooks should extend notifications.
 */
package pos.pos.notification.entity;
