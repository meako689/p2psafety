#
# Tastypie settings
#
TASTYPIE_ABSTRACT_APIKEY = False
API_LIMIT_PER_PAGE = 0

#
# django-allauth settings
#

# Login method to use
ACCOUNT_AUTHENTICATION_METHOD = 'username'

# Required during registration
ACCOUNT_EMAIL_REQUIRED = True

# Email verification is required
ACCOUNT_EMAIL_VERIFICATION = 'mandatory'
ACCOUNT_SIGNUP_FORM_CLASS = 'users.forms.SignupForm'

# Force user to supply additional fields after social signup
SOCIALACCOUNT_AUTO_SIGNUP = False

# Generate urls to https://
ACCOUNT_DEFAULT_HTTP_PROTOCOL = 'https'

# Redirect to main after email confirmation
ACCOUNT_EMAIL_CONFIRMATION_AUTHENTICATED_REDIRECT_URL = '/'

#
# PostGIS settings
#
SRID = {
    'default': 4326,  # WGS84, stored in database
    'projected': 900913,  # for Spatialite distance calculation
}
POSTGIS_TEMPLATE = 'template_postgis'

GOOGLE_API_KEY = NotImplemented
