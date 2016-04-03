# mm4s-examples

Sample implementations of [mm4s](https://github.com/jw3/mm4s)

## Basic Instructions for running a bot

1. Package your bot into Docker image
1. Deploy Mattermost
2. Deploy Dockerbot
3. Use Dockerbot REST API to deploy your bot

### Math Bot

Example of a well behaved bot that can do some basic math.

Initiate conversation with `@math`

See the help with `@math help`

>Command format `(lhs operation rhs)`
Four supported operations
- `+` addition
- `-` subtraction
- `*` multiplication
- `/` division

### Consul Bot

### Get Tweets Bot

An example file results being posted by a bot.

Should identify the capability of this pattern as a developer tool for fetching sample data.

### Status Bot

Example of tracking and reporting of job status.

Initiate conversation with `@statusbot`

Command formats
- `mock X` start task that runs for X seconds, returns task id 
- `check id` check completion status of id, return % of completion
- `isdone id` check completion status of id, returns boolean completion

### Chained Bots

Example of bot jobs together by sending result a to job b

## Bugs and Feedback

For bugs, questions and discussions please use the [Github Issues](https://github.com/jw3/mm4s-examples/issues).

## License

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

<https://www.apache.org/licenses/LICENSE-2.0>

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
