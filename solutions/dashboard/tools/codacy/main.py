import os
import asyncio
from issues import Issues
from const import MONGODB_CONNECTION_URL
from mongodb import MongoDB


class Main:
    def __init__(self):
        pass

    async def main(self):

        # Base64
        mongodb_credentials = {
            "username": os.environ.get("MONGODB_USERNAME"),
            "password": os.environ.get("MONGODB_PASSWORD")
        }
        codacy_api_token = os.environ.get("CODACY_API_TOKEN")

        # MongoDB
        mongodb = MongoDB()
        mongodb.create_connection_url(
            credentials=mongodb_credentials, connection_url=MONGODB_CONNECTION_URL)
        mongodb.handle_mongodb()
        # Create Initialization Documents
        # Because Logstash uses first document from mongodb for initialization
        mongodb.insertInitializationDocuments()

        # Headers
        headers = {
            'Content-Type': 'application/json',
            'Accept': 'application/json',
            'api-token': codacy_api_token
        }

        # Repositories
        while True:
            # Getting repositories list from CODACY
            # repos = Repositories(headers=headers)
            # repositories = repos.getRepositories()
            # repositories.sort()

            repositories = ['cortx', 'cortx-ha', 'cortx-hare', 'cortx-k8s', 'cortx-manager',
                            'cortx-mio', 'cortx-motr', 'cortx-motr-apps', 'cortx-prvsnr', 'cortx-re', 'cortx-rgw',
                            'cortx-rgw-integration', 'cortx-test']
            print(repositories)

            # issues
            issues = Issues(repositories=repositories,
                            mongodb=mongodb, headers=headers)
            issues.handle_issues()

            await asyncio.sleep(86400)


if __name__ == "__main__":
    main = Main()

    loop = asyncio.get_event_loop()
    loop.create_task(main.main())
    loop.run_forever()
