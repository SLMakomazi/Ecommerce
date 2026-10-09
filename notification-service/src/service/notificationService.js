const repository = require('../repository/notificationRepository');
const { message } = require('../model/notification');
async function record(event) {
  await repository.save(event, message(event));
}
module.exports = { record, list: repository.list };
