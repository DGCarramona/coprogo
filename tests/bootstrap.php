<?php

use Symfony\Component\Dotenv\Dotenv;

require dirname(__DIR__).'/vendor/autoload.php';

(new Dotenv())->bootEnv(dirname(__DIR__).'/.env');

$appDebug = filter_var($_SERVER['APP_DEBUG'] ?? false, FILTER_VALIDATE_BOOL);

if ($appDebug === true) {
    umask(0000);
}
