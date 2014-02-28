from django.core.urlresolvers import reverse
from django.test import TestCase

from .helpers.mixins import UsersMixin
from .helpers.factories import EventFactory
from users.tests.helpers import UserFactory


class ViewsTestCase(UsersMixin, TestCase):

    def test_events_map(self):
        url = reverse('events:map')
        self.assertEqual(self.client.get(url).status_code, 302)
        self.login_as(self.events_granted_user)
        self.assertEqual(self.client.get(url).status_code, 200)

    #def test_add_event_update(self):
     #   user = UserFactory()
    #    event = EventFactory(user=user)

     #   self.login_as_superuser()

     #   url = reverse('operator_add_eventupdate')
     #   data = dict(event_id=event, text="test")
     #   response = self.client.post(url, data=data)
     #   self.assertEqual(response.status_code, 200)
