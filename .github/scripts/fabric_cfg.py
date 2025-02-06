from fabric.api import env # type: ignore

# Common fabric settings
env.output_prefix = False
env.abort_on_prompts = True
env.timeout = 100
env.connection_attempts = 10
env.disable_known_hosts = True
env.keepalive = 60 # keep long SSH connections running
