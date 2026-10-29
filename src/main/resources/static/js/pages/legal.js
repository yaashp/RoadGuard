// Privacy policy and terms of service. Plain-language templates: have them reviewed before a public launch.
import { h, mount, pageHeader } from '../ui.js';

const UPDATED = 'Last updated: October 2026';

export async function privacyPage({ container }) {
  mount(container, pageHeader('Privacy policy', UPDATED),
    h('article', { class: 'card legal' },
      h('h2', null, 'Who we are'),
      h('p', null, 'RoadGuard is a website for reporting road hazards, tracking their repair and comparing route safety. You can contact us through the ', h('a', { href: '/contact' }, 'contact page'), '.'),
      h('h2', null, 'What we collect'),
      h('p', null, 'Account details (your name and email address; your password is stored only as a salted hash). Road hazard reports you submit, including the hazard type, description, location, road name and an optional photo. Community posts, comments and votes. Saved routes and notification settings. Messages you send through the contact form. Basic technical data needed to run the site, such as session and security cookies and your theme preference, which is stored in your browser.'),
      h('h2', null, 'How we use it'),
      h('p', null, 'To run the service, show reports on the map, calculate route safety estimates, send updates about your reports, prevent spam and abuse, and answer your messages.'),
      h('h2', null, 'Location'),
      h('p', null, 'When you use "Near me" or pick a point on the map, your location is used in your browser. It is sent to our server only for the request you make, such as a report or a route search.'),
      h('h2', null, 'Third-party services'),
      h('p', null, 'The map uses OpenStreetMap tiles. Routes and place searches are sent to route and geocoding services. If the site operator has enabled Google Analytics, it records page views. These services receive only the data needed for the request and follow their own privacy policies.'),
      h('h2', null, 'Public information'),
      h('p', null, 'Reports, community posts, comments and confirmations can be seen by other visitors. Do not include personal details, such as phone numbers or other people\'s identities, in public text or photos.'),
      h('h2', null, 'How long we keep it'),
      h('p', null, 'We keep account data while your account exists. Reports and messages are kept while they are needed to run and improve the service. You can ask us to delete your account or a message through the contact page.'),
      h('h2', null, 'Your rights'),
      h('p', null, 'You can ask to see, correct or delete the personal information we hold about you, or to object to how we use it. Send your request through the contact page.'),
      h('h2', null, 'Security'),
      h('p', null, 'Passwords are hashed, login attempts are limited, and forms are protected against cross-site requests. No online service is completely secure, so please use a password you do not use elsewhere.'),
      h('h2', null, 'Changes'),
      h('p', null, 'If we change this policy, we will update the date at the top of this page.')));
}

export async function termsPage({ container }) {
  mount(container, pageHeader('Terms of service', UPDATED),
    h('article', { class: 'card legal' },
      h('h2', null, 'Using RoadGuard'),
      h('p', null, 'By using RoadGuard you agree to these terms. If you do not agree, please do not use the site.'),
      h('h2', null, 'Accounts'),
      h('p', null, 'Keep your password private and give accurate information when you register. You are responsible for activity on your account.'),
      h('h2', null, 'Reports and community content'),
      h('p', null, 'Report honestly. Do not post false, abusive, hateful or unlawful content, personal information about other people, or photos of people taken without their permission. We may hide, edit or remove content and suspend accounts that break these rules.'),
      h('h2', null, 'Your content'),
      h('p', null, 'You keep ownership of what you submit. You give RoadGuard permission to store and display it on the site so the service can work, for example on the map and in community pages.'),
      h('h2', null, 'Accuracy of information'),
      h('p', null, 'Reports and community posts come from users and may be incomplete, outdated or wrong. Route safety scores are estimates based on those reports.'),
      h('h2', null, 'Emergencies'),
      h('p', null, 'Do not rely on RoadGuard in an emergency. If someone is hurt or in danger, contact your local emergency services.'),
      h('h2', null, 'Acceptable use'),
      h('p', null, 'Do not attack or disrupt the service, scrape it in bulk, or interfere with other users.'),
      h('h2', null, 'Availability and liability'),
      h('p', null, 'RoadGuard may change, pause or end features at any time. To the extent the law allows, RoadGuard is not liable for loss caused by using the site.'),
      h('h2', null, 'Changes and contact'),
      h('p', null, 'We may update these terms. Continued use after an update means you accept the new terms. Questions can be sent through the ', h('a', { href: '/contact' }, 'contact page'), '.')));
}
