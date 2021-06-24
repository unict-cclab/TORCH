<?php

/*
|--------------------------------------------------------------------------
| Web Routes
|--------------------------------------------------------------------------
|
| Here is where you can register web routes for your application. These
| routes are loaded by the RouteServiceProvider within a group which
| contains the "web" middleware group. Now create something great!
|
*/

Route::get('/', function () {
    return view('welcome');
});

Auth::routes();

Route::get('/home', 'HomeController@index')->name('home');
Route::resource('user', 'UserController', [
    'names' =>
    [
        'show' => 'user.show'
    ]
])->middleware('auth');

Route::post('user/{id}/template/validate', 'TemplateController@validateTemplate')->middleware('auth')->name('user.template.validate');
Route::resource('user.template', 'TemplateController')->middleware('auth');

Route::post('bpmn-engine','BPMNEngineController@launch')->name('bpmn-engine');
Route::get('/template-modeler', 'TemplateController@createWithModeler')->middleware('auth')->name('template.modeler');