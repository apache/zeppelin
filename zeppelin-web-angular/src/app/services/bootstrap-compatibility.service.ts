/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class BootstrapCompatibilityService {
  private bootstrapStylesInjected = false;
  private readonly styleId = 'zeppelin-bootstrap-compat-styles';

  // Bootstrap compatibility styles for classic visualizations
  private readonly bootstrapCompatStyles = `
    /* Bootstrap compatibility styles for classic visualization templates */
    .panel {
      margin-bottom: 20px;
      background-color: #fff;
      border: 1px solid #ddd;
      border-radius: 4px;
      box-shadow: 0 1px 1px rgba(0, 0, 0, 0.05);
    }
    .panel-heading {
      padding: 10px 15px;
      border-bottom: 1px solid #ddd;
      border-top-left-radius: 3px;
      border-top-right-radius: 3px;
      background-color: #f5f5f5;
    }
    .panel-body {
      padding: 15px;
    }
    .btn {
      display: inline-block;
      margin-bottom: 0;
      font-weight: 400;
      text-align: center;
      white-space: nowrap;
      vertical-align: middle;
      cursor: pointer;
      user-select: none;
      background-image: none;
      border: 1px solid transparent;
      padding: 6px 12px;
      font-size: 14px;
      line-height: 1.42857143;
      border-radius: 4px;
      text-decoration: none;
    }
    .btn:hover, .btn:focus {
      color: #333;
      text-decoration: none;
    }
    .btn:active {
      background-image: none;
      outline: 0;
      box-shadow: inset 0 3px 5px rgba(0, 0, 0, 0.125);
    }
    .btn-default {
      color: #333;
      background-color: #fff;
      border-color: #ccc;
    }
    .btn-default:hover, .btn-default:focus {
      color: #333;
      background-color: #e6e6e6;
      border-color: #adadad;
    }
    .btn-default:active {
      color: #333;
      background-color: #e6e6e6;
      border-color: #adadad;
    }
    .btn-primary {
      color: #fff;
      background-color: #337ab7;
      border-color: #2e6da4;
    }
    .btn-primary:hover, .btn-primary:focus {
      color: #fff;
      background-color: #286090;
      border-color: #204d74;
    }
    .btn-xs {
      padding: 1px 5px;
      font-size: 12px;
      line-height: 1.5;
      border-radius: 3px;
    }
    .btn-sm {
      padding: 5px 10px;
      font-size: 12px;
      line-height: 1.5;
      border-radius: 3px;
    }
    .btn-group {
      position: relative;
      display: inline-block;
      vertical-align: middle;
    }
    .btn-group > .btn {
      position: relative;
      float: left;
    }
    .btn-group > .btn:hover, .btn-group > .btn:focus, .btn-group > .btn:active {
      z-index: 2;
    }
    .btn-group .btn + .btn {
      margin-left: -1px;
    }
    .btn-group > .btn:not(:first-child):not(:last-child) {
      border-radius: 0;
    }
    .btn-group > .btn:first-child:not(:last-child) {
      border-top-right-radius: 0;
      border-bottom-right-radius: 0;
    }
    .btn-group > .btn:last-child:not(:first-child) {
      border-top-left-radius: 0;
      border-bottom-left-radius: 0;
    }
    .dropdown-menu {
      position: absolute;
      top: 100%;
      left: 0;
      z-index: 1000;
      display: none;
      float: left;
      min-width: 160px;
      padding: 5px 0;
      margin: 2px 0 0;
      font-size: 14px;
      text-align: left;
      list-style: none;
      background-color: #fff;
      border: 1px solid #ccc;
      border-radius: 4px;
      box-shadow: 0 6px 12px rgba(0, 0, 0, 0.175);
    }
    .dropdown-menu > li > a {
      display: block;
      padding: 3px 20px;
      clear: both;
      font-weight: 400;
      line-height: 1.42857143;
      color: #333;
      white-space: nowrap;
      text-decoration: none;
    }
    .dropdown-menu > li > a:hover, .dropdown-menu > li > a:focus {
      color: #262626;
      background-color: #f5f5f5;
    }
    .caret {
      display: inline-block;
      width: 0;
      height: 0;
      margin-left: 2px;
      vertical-align: middle;
      border-top: 4px solid;
      border-right: 4px solid transparent;
      border-left: 4px solid transparent;
    }
    .label {
      display: inline;
      padding: 0.2em 0.6em 0.3em;
      font-size: 75%;
      font-weight: 700;
      line-height: 1;
      color: #fff;
      text-align: center;
      white-space: nowrap;
      vertical-align: baseline;
      border-radius: 0.25em;
    }
    .label-default {
      background-color: #777;
    }
    .row {
      margin-right: -15px;
      margin-left: -15px;
    }
    .row:before, .row:after {
      display: table;
      content: " ";
    }
    .row:after {
      clear: both;
    }
    .col-xs-1, .col-sm-1, .col-md-1, .col-lg-1,
    .col-xs-2, .col-sm-2, .col-md-2, .col-lg-2,
    .col-xs-3, .col-sm-3, .col-md-3, .col-lg-3,
    .col-xs-4, .col-sm-4, .col-md-4, .col-lg-4,
    .col-xs-5, .col-sm-5, .col-md-5, .col-lg-5,
    .col-xs-6, .col-sm-6, .col-md-6, .col-lg-6,
    .col-xs-7, .col-sm-7, .col-md-7, .col-lg-7,
    .col-xs-8, .col-sm-8, .col-md-8, .col-lg-8,
    .col-xs-9, .col-sm-9, .col-md-9, .col-lg-9,
    .col-xs-10, .col-sm-10, .col-md-10, .col-lg-10,
    .col-xs-11, .col-sm-11, .col-md-11, .col-lg-11,
    .col-xs-12, .col-sm-12, .col-md-12, .col-lg-12 {
      position: relative;
      min-height: 1px;
      padding-right: 15px;
      padding-left: 15px;
    }
    .col-xs-1, .col-xs-2, .col-xs-3, .col-xs-4, .col-xs-5, .col-xs-6, .col-xs-7, .col-xs-8, .col-xs-9, .col-xs-10, .col-xs-11, .col-xs-12 {
      float: left;
    }
    .col-xs-12 { width: 100%; }
    .col-xs-11 { width: 91.66666667%; }
    .col-xs-10 { width: 83.33333333%; }
    .col-xs-9 { width: 75%; }
    .col-xs-8 { width: 66.66666667%; }
    .col-xs-7 { width: 58.33333333%; }
    .col-xs-6 { width: 50%; }
    .col-xs-5 { width: 41.66666667%; }
    .col-xs-4 { width: 33.33333333%; }
    .col-xs-3 { width: 25%; }
    .col-xs-2 { width: 16.66666667%; }
    .col-xs-1 { width: 8.33333333%; }
    @media (min-width: 768px) {
      .col-sm-1, .col-sm-2, .col-sm-3, .col-sm-4, .col-sm-5, .col-sm-6, .col-sm-7, .col-sm-8, .col-sm-9, .col-sm-10, .col-sm-11, .col-sm-12 {
        float: left;
      }
      .col-sm-12 { width: 100%; }
      .col-sm-11 { width: 91.66666667%; }
      .col-sm-10 { width: 83.33333333%; }
      .col-sm-9 { width: 75%; }
      .col-sm-8 { width: 66.66666667%; }
      .col-sm-7 { width: 58.33333333%; }
      .col-sm-6 { width: 50%; }
      .col-sm-5 { width: 41.66666667%; }
      .col-sm-4 { width: 33.33333333%; }
      .col-sm-3 { width: 25%; }
      .col-sm-2 { width: 16.66666667%; }
      .col-sm-1 { width: 8.33333333%; }
    }
    @media (min-width: 992px) {
      .col-md-1, .col-md-2, .col-md-3, .col-md-4, .col-md-5, .col-md-6, .col-md-7, .col-md-8, .col-md-9, .col-md-10, .col-md-11, .col-md-12 {
        float: left;
      }
      .col-md-12 { width: 100%; }
      .col-md-11 { width: 91.66666667%; }
      .col-md-10 { width: 83.33333333%; }
      .col-md-9 { width: 75%; }
      .col-md-8 { width: 66.66666667%; }
      .col-md-7 { width: 58.33333333%; }
      .col-md-6 { width: 50%; }
      .col-md-5 { width: 41.66666667%; }
      .col-md-4 { width: 33.33333333%; }
      .col-md-3 { width: 25%; }
      .col-md-2 { width: 16.66666667%; }
      .col-md-1 { width: 8.33333333%; }
    }
    .list-unstyled {
      padding-left: 0;
      list-style: none;
    }
    .form-group {
      margin-bottom: 15px;
    }
    .form-control {
      display: block;
      width: 100%;
      height: 34px;
      padding: 6px 12px;
      font-size: 14px;
      line-height: 1.42857143;
      color: #555;
      background-color: #fff;
      border: 1px solid #ccc;
      border-radius: 4px;
      box-shadow: inset 0 1px 1px rgba(0, 0, 0, 0.075);
      transition: border-color ease-in-out 0.15s, box-shadow ease-in-out 0.15s;
    }
    .form-control:focus {
      border-color: #66afe9;
      outline: 0;
      box-shadow: inset 0 1px 1px rgba(0, 0, 0, 0.075), 0 0 8px rgba(102, 175, 233, 0.6);
    }
    .input-sm {
      height: 30px;
      padding: 5px 10px;
      font-size: 12px;
      line-height: 1.5;
      border-radius: 3px;
    }
    .input-group {
      position: relative;
      display: table;
      border-collapse: separate;
    }
    .input-group .form-control {
      position: relative;
      z-index: 2;
      float: left;
      width: 100%;
      margin-bottom: 0;
    }
    .input-group-addon {
      padding: 6px 12px;
      font-size: 14px;
      font-weight: 400;
      line-height: 1;
      color: #555;
      text-align: center;
      background-color: #eee;
      border: 1px solid #ccc;
      border-radius: 4px;
      display: table-cell;
      white-space: nowrap;
      vertical-align: middle;
    }
    .table {
      width: 100%;
      max-width: 100%;
      margin-bottom: 20px;
      background-color: transparent;
    }
    .table > thead > tr > th,
    .table > tbody > tr > th,
    .table > tfoot > tr > th,
    .table > thead > tr > td,
    .table > tbody > tr > td,
    .table > tfoot > tr > td {
      padding: 8px;
      line-height: 1.42857143;
      vertical-align: top;
      border-top: 1px solid #ddd;
    }
    .table > thead > tr > th {
      vertical-align: bottom;
      border-bottom: 2px solid #ddd;
    }
    .table-striped > tbody > tr:nth-of-type(odd) {
      background-color: #f9f9f9;
    }
    .radio-inline {
      position: relative;
      display: inline-block;
      padding-left: 20px;
      margin-bottom: 0;
      font-weight: 400;
      vertical-align: middle;
      cursor: pointer;
    }
    .radio-inline input[type="radio"] {
      position: absolute;
      margin-left: -20px;
    }
    .form-check {
      position: relative;
      display: block;
      margin-bottom: 0.5rem;
    }
    .glyphicon {
      position: relative;
      top: 1px;
      display: inline-block;
      font-style: normal;
      font-weight: 400;
      line-height: 1;
    }
    .glyphicon-ok:before { content: "✓"; }
    .glyphicon-floppy-disk:before { content: "💾"; }
    .noDot {
      list-style: none;
      padding: 0;
      margin: 0;
    }
    .liVertical {
      display: inline-block;
      margin: 2px;
    }
    .columns {
      display: block;
      height: 100%;
    }
    .lightBold {
      font-weight: 500;
    }
    .network-labels {
      margin: 5px;
    }
    .network-badge-settings {
      margin-bottom: 10px;
    }

    /* Table Display styles from paragraph.css */
    .tableDisplay {
      margin-top: 2px;
    }
    .tableDisplay img {
      display: block;
      max-width: 100%;
      height: auto;
    }
    .tableDisplay .btn-group span {
      margin: 10px 0 0 10px;
      font-size: 12px;
    }
    .tableDisplay .btn-group span a {
      cursor: pointer;
    }
    .tableDisplay .option {
      padding: 5px 5px 5px 5px;
      font-size: 12px;
      height: auto;
      overflow: auto;
      border-top: 1px solid #ecf0f1;
    }
    .tableDisplay .option .columns {
      height: 100%;
    }
    .tableDisplay .option .columns ul {
      background: white;
      overflow: auto;
      width: auto;
      padding: 3px 3px 3px 3px;
      height: 150px;
      border: 1px solid #CCC;
      box-shadow: inset 0 1px 1px rgba(0, 0, 0, .075);
      -webkit-transition: border-color ease-in-out .15s, -webkit-box-shadow ease-in-out .15s;
      -o-transition: border-color ease-in-out .15s, box-shadow ease-in-out .15s;
      transition: border-color ease-in-out .15s, box-shadow ease-in-out .15s;
    }
    .tableDisplay .option .columns ul li {
      margin: 3px 3px 3px 3px;
    }
    .tableDisplay .option .columns ul li span {
      cursor: pointer;
      margin-left: 3px;
      margin-top: 3px;
    }
    .tableDisplay .option .columns ul li div ul {
      width: auto;
      height: auto;
    }
    .tableDisplay .option .columns ul li div ul li a {
      padding: 0;
      margin: 0;
      cursor: pointer;
    }
    .tableDisplay .option .columns a:focus,
    .tableDisplay .option .columns a:hover {
      text-decoration: none;
      outline: 0;
      outline-offset: 0;
    }
    .paragraph .tableDisplay .hljs {
      background: none;
    }
  `;

  /**
   * Inject Bootstrap compatibility styles into the document head
   * This ensures that external AngularJS templates can use Bootstrap classes
   */
  injectBootstrapStyles(): void {
    if (this.bootstrapStylesInjected) {
      return; // Already injected
    }

    // Check if styles already exist in DOM
    if (document.getElementById(this.styleId)) {
      this.bootstrapStylesInjected = true;
      return;
    }

    const styleElement = document.createElement('style');
    styleElement.id = this.styleId;
    styleElement.type = 'text/css';
    styleElement.appendChild(document.createTextNode(this.bootstrapCompatStyles));

    // Insert at the beginning of head to allow overrides
    const head = document.getElementsByTagName('head')[0];
    head.insertBefore(styleElement, head.firstChild);

    this.bootstrapStylesInjected = true;
  }
}
